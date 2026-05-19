package com.example.phm.community.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.example.phm.alarm.entity.Alarm;
import com.example.phm.alarm.repository.AlarmRepository;
import com.example.phm.auth.entity.User;
import com.example.phm.auth.repository.UserRepository;
import com.example.phm.community.dto.ChatMessageCreateRequest;
import com.example.phm.community.dto.ChatMessageResponse;
import com.example.phm.community.dto.ChatRoomResponse;
import com.example.phm.community.dto.DirectChatRoomRequest;
import com.example.phm.community.dto.FactoryReportResponse;
import com.example.phm.community.dto.LineGroupResponse;
import com.example.phm.community.entity.ChatMessage;
import com.example.phm.community.entity.ChatRoom;
import com.example.phm.community.repository.ChatMessageRepository;
import com.example.phm.community.repository.ChatRoomRepository;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.line.dto.LineResponse;
import com.example.phm.line.entity.ProductionLine;
import com.example.phm.line.repository.ProductionLineRepository;
import com.example.phm.line.service.LineAggregationService;
import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.SensorFrame;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityService {

    private static final double GRID_EMISSION_FACTOR_KG_PER_KWH = 0.478;

    private final UserRepository userRepository;
    private final ProductionLineRepository lineRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LineAggregationService lineAggregationService;
    private final EquipmentRepository equipmentRepository;
    private final AlarmRepository alarmRepository;
    private final SensorBufferRegistry sensorBufferRegistry;

    public CommunityService(
            UserRepository userRepository,
            ProductionLineRepository lineRepository,
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            LineAggregationService lineAggregationService,
            EquipmentRepository equipmentRepository,
            AlarmRepository alarmRepository,
            SensorBufferRegistry sensorBufferRegistry
    ) {
        this.userRepository = userRepository;
        this.lineRepository = lineRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.lineAggregationService = lineAggregationService;
        this.equipmentRepository = equipmentRepository;
        this.alarmRepository = alarmRepository;
        this.sensorBufferRegistry = sensorBufferRegistry;
    }

    @Transactional(readOnly = true)
    public List<LineGroupResponse> lineGroups() {
        List<User> users = userRepository.findAll();
        return lineRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductionLine::getLineId))
                .map(line -> new LineGroupResponse(
                        line.getLineId(),
                        line.getLineName(),
                        users.stream()
                                .filter(user -> line.getLineId().equals(user.getLineId()))
                                .filter(user -> role(user).equals("MANAGER"))
                                .map(this::brief)
                                .toList(),
                        users.stream()
                                .filter(user -> line.getLineId().equals(user.getLineId()))
                                .filter(user -> role(user).equals("OPERATOR"))
                                .map(this::brief)
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public List<ChatRoomResponse> rooms(String currentUserId) {
        User currentUser = user(currentUserId);
        ensureLineRooms();
        List<ChatRoom> all = chatRoomRepository.findAll();
        return all.stream()
                .filter(room -> canAccessRoom(currentUser, room))
                .sorted(Comparator.comparing(ChatRoom::getLineId).thenComparing(ChatRoom::getChatRoomId))
                .map(ChatRoomResponse::from)
                .toList();
    }

    @Transactional
    public ChatRoomResponse directRoom(DirectChatRoomRequest request) {
        User requester = user(request.requesterUserId());
        User target = user(request.targetUserId());
        if (!canDirectMessage(requester, target)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct chat is not allowed for this user pair");
        }
        String userA = request.requesterUserId().compareTo(request.targetUserId()) <= 0
                ? request.requesterUserId()
                : request.targetUserId();
        String userB = userA.equals(request.requesterUserId()) ? request.targetUserId() : request.requesterUserId();
        ChatRoom room = chatRoomRepository.findByRoomTypeAndUserAIdAndUserBId("DIRECT", userA, userB)
                .orElseGet(() -> createDirectRoom(requester, target, userA, userB));
        return ChatRoomResponse.from(room);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(Long roomId, String currentUserId) {
        User currentUser = user(currentUserId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
        if (!canAccessRoom(currentUser, room)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat room is not allowed");
        }
        return chatMessageRepository.findTop100ByChatRoomIdAndDeletedFalseOrderBySentAtAsc(roomId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional
    public ChatMessageResponse send(Long roomId, ChatMessageCreateRequest request) {
        User sender = user(request.senderUserId());
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
        if (!canAccessRoom(sender, room)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat room is not allowed");
        }
        ChatMessage message = new ChatMessage();
        message.setChatRoomId(roomId);
        message.setSenderUserId(request.senderUserId());
        message.setMessageContent(request.messageContent());
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public FactoryReportResponse factoryReport(String type) {
        String reportType = normalizeReportType(type);
        FactorySnapshot snapshot = factorySnapshot();
        String title = switch (reportType) {
            case "heat_safety" -> "폭염 안전관리 보고서";
            case "annual_esg" -> "연간 ESG 운영 보고서";
            case "energy_emission" -> "전력 사용 및 탄소배출 보고서";
            default -> "UECADA 공장 현황 자동 문서";
        };
        String markdown = switch (reportType) {
            case "heat_safety" -> heatSafetyReport(snapshot);
            case "annual_esg" -> annualEsgReport(snapshot);
            case "energy_emission" -> energyEmissionReport(snapshot);
            default -> factorySummaryReport(snapshot);
        };
        return new FactoryReportResponse(Instant.now(), reportType, title, markdown);
    }

    private String normalizeReportType(String type) {
        if (type == null || type.isBlank()) return "factory";
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "heat", "heat_safety", "heat-safety" -> "heat_safety";
            case "esg", "annual_esg", "annual-esg" -> "annual_esg";
            case "energy", "energy_emission", "energy-emission" -> "energy_emission";
            default -> "factory";
        };
    }

    private String factorySummaryReport(FactorySnapshot s) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# UECADA 공장 현황 자동 문서\n\n");
        appendDocumentInfo(markdown, "UECADA 공장 현황 자동 문서", "실시간 공장 요약", s.today(), s.today());
        markdown.append("## 1. 핵심 요약\n\n");
        markdown.append("- 전체 설비: ").append(s.equipmentTotal()).append("대\n");
        markdown.append("- 가동: ").append(s.running()).append("대, 대기/보전: ").append(s.standby()).append("대, 이상/알람: ").append(s.alarm()).append("대\n");
        markdown.append("- 평균 OEE: ").append(round1(s.avgOee())).append("%\n");
        markdown.append("- OPEN 알람: ").append(s.openAlarms()).append("건\n");
        markdown.append("- 추정 전력: ").append(round2(s.totalPowerKw())).append(" kW\n");
        markdown.append("- 추정 탄소배출량: ").append(round2(s.hourlyEmissionsKg()))
                .append(" kgCO2e/h\n\n");
        appendLineStatus(markdown, s.lines());
        appendRecommendation(markdown, s);
        return markdown.toString();
    }

    private String heatSafetyReport(FactorySnapshot s) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 폭염 안전관리 보고서\n\n");
        appendDocumentInfo(markdown, "폭염 안전관리 보고서", "실시간 버퍼 기반 자동 보고", s.today(), s.today());

        markdown.append("## 2. 적용 범위\n\n");
        markdown.append("본 보고서는 UECADA SmartFactory의 전체 라인에 설치된 설비 및 인접 센서에서 수집한 온도 데이터를 기준으로 작성한다. ")
                .append("보고 대상에는 설비별 측정값, 경보 발생 이력, 작업조정 및 작업중지 판단 기준이 포함된다.\n\n");

        markdown.append("## 3. 관리 기준\n\n");
        markdown.append("| 구분 | 기준값 | 관리 기준 | 조치 기준 |\n");
        markdown.append("|------|--------|----------|-----------|\n");
        markdown.append("| 관심 | 30 °C 이상 | 모니터링 강화 | 냉방·환기 상태 확인 |\n");
        markdown.append("| 주의 | 33 °C 이상 | 휴식 제공 검토 | 수분 공급, 휴식시간 부여 |\n");
        markdown.append("| 경고 | 35 °C 이상 | 고위험 작업 제한 검토 | 작업시간 조정, 관리자 승인 |\n");
        markdown.append("| 심각 | 38 °C 이상 | 작업중지 검토 | 비상대응, 긴급작업 외 작업 제한 |\n\n");

        markdown.append("## 4. 주요 현황\n\n");
        markdown.append("### 4.1 요약\n\n");
        markdown.append("현재 최고 온도는 ").append(round2(s.maxTemperature())).append(" °C, 평균 온도는 ")
                .append(round2(s.avgTemperature())).append(" °C로 집계되었다. 주의 이상 설비는 ")
                .append(s.cautionHeatCount()).append("대, 경고 이상 설비는 ").append(s.warningHeatCount())
                .append("대이다. 고온 구간 발생 시 냉방·환기 상태 확인, 수분 공급, 작업시간 조정, 관리자 승인 절차를 우선 적용한다.\n\n");

        markdown.append("### 4.2 관리 지표\n\n");
        markdown.append("| 지표 | 값 |\n|------|----|\n");
        markdown.append("| 평균 온도 | ").append(round2(s.avgTemperature())).append(" °C |\n");
        markdown.append("| 최고 온도 | ").append(round2(s.maxTemperature())).append(" °C |\n");
        markdown.append("| 최저 온도 | ").append(round2(s.minTemperature())).append(" °C |\n");
        markdown.append("| 관심 이상 설비 수 | ").append(s.attentionHeatCount()).append("대 |\n");
        markdown.append("| 주의 이상 설비 수 | ").append(s.cautionHeatCount()).append("대 |\n");
        markdown.append("| 경고 이상 설비 수 | ").append(s.warningHeatCount()).append("대 |\n");
        markdown.append("| 심각 이상 설비 수 | ").append(s.dangerHeatCount()).append("대 |\n");
        markdown.append("| 경보 발생 설비 수 | ").append(s.openAlarmEquipments()).append("대 |\n");
        markdown.append("| 작업조정 필요 건수 | ").append(s.warningHeatCount()).append("건 |\n");
        markdown.append("| 작업중지 검토 건수 | ").append(s.dangerHeatCount()).append("건 |\n\n");

        markdown.append("## 5. 조치 내역\n\n");
        markdown.append("- 실시간 온도 모니터링 결과, 기준 온도 초과 구간을 상시 식별하였다.\n");
        markdown.append("- 35 °C 이상 설비는 ").append(s.warningHeatCount()).append("대이며 작업시간 조정 검토 대상이다.\n");
        markdown.append("- 38 °C 이상 설비는 ").append(s.dangerHeatCount()).append("대이며 작업중지 또는 긴급작업 전환 검토 대상이다.\n");
        markdown.append("- 개선조치로 환기량 점검, 고온 설비 주변 체류시간 제한, 고온 알람 발생 시 관리자 승인 절차를 적용한다.\n\n");

        appendHeatEquipmentTable(markdown, s);
        markdown.append("## 7. 점검 의견\n\n");
        markdown.append("현재 버퍼 기준 온도와 OPEN 알람을 함께 확인한 결과, 고온 설비는 라인 관리자에게 즉시 공유하고 작업자 휴식·환기·냉각 상태를 우선 점검한다.\n\n");
        appendApproval(markdown);
        return markdown.toString();
    }

    private String annualEsgReport(FactorySnapshot s) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 연간 ESG 운영 보고서\n\n");
        appendDocumentInfo(markdown, "연간 ESG 운영 보고서", "실시간 버퍼 기반 연간/월별/일별 환산", s.yearStart(), s.today());

        markdown.append("## 2. 보고 범위\n\n");
        markdown.append("본 보고서는 UECADA SmartFactory의 설비별 실시간 센서 데이터(온도, 전압, 전류)를 기준으로 폭염 안전관리 현황과 전력 사용에 따른 탄소배출 현황을 통합하여 작성한다. ")
                .append("월별·일별 값은 현재 버퍼의 순간 전력을 기준으로 자동 환산한 운영 추정치이다.\n\n");

        markdown.append("## 3. 운영 기준\n\n");
        markdown.append("- 순간 전력: `power_w = voltage_v * current_a`\n");
        markdown.append("- 전력량: `energy_kwh = Σ(power_w * interval_hour) / 1000`\n");
        markdown.append("- 탄소배출량: `emissions_tco2eq = energy_kwh * emission_factor_tco2_per_kwh`\n");
        markdown.append("- 적용 배출계수: ").append(GRID_EMISSION_FACTOR_KG_PER_KWH)
                .append(" kgCO2e/kWh (").append(GRID_EMISSION_FACTOR_KG_PER_KWH / 1000.0).append(" tCO2eq/kWh)\n\n");

        markdown.append("## 4. 안전 부문\n\n");
        markdown.append("보고 시점 최고 온도는 ").append(round2(s.maxTemperature())).append(" °C, 평균 온도는 ")
                .append(round2(s.avgTemperature())).append(" °C이다. 경고 이상 설비는 ")
                .append(s.warningHeatCount()).append("대, 심각 이상 설비는 ").append(s.dangerHeatCount()).append("대이다.\n\n");

        markdown.append("## 5. 환경 부문\n\n");
        markdown.append("| 지표 | 값 |\n|------|----|\n");
        markdown.append("| 측정 설비 수 | ").append(s.measuredEquipmentCount()).append("대 |\n");
        markdown.append("| 평균 전압 | ").append(round2(s.avgVoltage())).append(" V |\n");
        markdown.append("| 평균 전류 | ").append(round2(s.avgCurrent())).append(" A |\n");
        markdown.append("| 평균 전력 | ").append(round2(s.avgPowerW())).append(" W |\n");
        markdown.append("| 최대 전력 | ").append(round2(s.maxPowerW())).append(" W |\n");
        markdown.append("| 일별 환산 전력사용량 | ").append(round2(s.dailyEnergyKwh())).append(" kWh |\n");
        markdown.append("| 월별 환산 전력사용량 | ").append(round2(s.monthlyEnergyKwh())).append(" kWh |\n");
        markdown.append("| 연간 환산 전력사용량 | ").append(round2(s.annualEnergyKwh())).append(" kWh |\n");
        markdown.append("| 연간 환산 탄소배출량 | ").append(round4(s.annualEmissionsTco2())).append(" tCO2eq |\n");
        markdown.append("| 설비당 평균 배출량 | ").append(round4(s.emissionsPerEquipmentTco2())).append(" tCO2eq |\n\n");

        markdown.append("### 5.1 일별·월별·연간 환산\n\n");
        markdown.append("| 구분 | 전력사용량(kWh) | 탄소배출량(tCO2eq) |\n");
        markdown.append("|------|-----------------|--------------------|\n");
        markdown.append("| 일별 | ").append(round2(s.dailyEnergyKwh())).append(" | ").append(round4(s.dailyEmissionsTco2())).append(" |\n");
        markdown.append("| 월별 | ").append(round2(s.monthlyEnergyKwh())).append(" | ").append(round4(s.monthlyEmissionsTco2())).append(" |\n");
        markdown.append("| 연간 | ").append(round2(s.annualEnergyKwh())).append(" | ").append(round4(s.annualEmissionsTco2())).append(" |\n\n");

        appendIntegratedEquipmentTable(markdown, s);
        markdown.append("## 6. 종합 의견\n\n");
        markdown.append("- 폭염 안전관리와 전력 사용 모니터링 결과를 기준으로 주요 리스크를 관리하였다.\n");
        markdown.append("- 전력 사용량과 탄소배출량은 설비별 활동데이터 기준으로 집계하였다.\n");
        markdown.append("- 향후 개선계획은 전류·전압 급등 설비의 부하 원인 분석, 고온 설비 냉각계 점검, 라인별 OEE 저하 원인 개선으로 관리한다.\n\n");
        appendApproval(markdown);
        return markdown.toString();
    }

    private String energyEmissionReport(FactorySnapshot s) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 전력 사용 및 탄소배출 보고서\n\n");
        appendDocumentInfo(markdown, "전력 사용 및 탄소배출 보고서", "실시간 버퍼 기반 자동 산정", s.today(), s.today());

        markdown.append("## 2. 적용 범위\n\n");
        markdown.append("본 보고서는 UECADA SmartFactory 전체 설비에서 수집한 전압, 전류, 타임스탬프 데이터를 기준으로 작성한다. ")
                .append("전력 사용량은 측정 구간별 전압과 전류를 활용하여 산정하며, 탄소배출량은 전력 사용량에 적용 배출계수를 반영하여 계산한다.\n\n");

        markdown.append("## 3. 산정 기준\n\n");
        markdown.append("- 순간 전력: `power_w = voltage_v * current_a`\n");
        markdown.append("- 전력량: `energy_kwh = Σ(power_w * interval_hour) / 1000`\n");
        markdown.append("- 탄소배출량: `emissions_tco2eq = energy_kwh * emission_factor_tco2_per_kwh`\n\n");
        markdown.append("| 항목 | 값 |\n|------|----|\n");
        markdown.append("| 전력 배출계수 | ").append(GRID_EMISSION_FACTOR_KG_PER_KWH / 1000.0).append(" tCO2eq/kWh |\n");
        markdown.append("| 데이터 집계 주기 | 실시간 버퍼 2초 수집 / 보고서 생성 시점 환산 |\n");
        markdown.append("| 측정 설비 수 | ").append(s.measuredEquipmentCount()).append("대 |\n\n");

        markdown.append("## 4. 주요 현황\n\n");
        markdown.append("보고 시점의 총 전력은 ").append(round2(s.totalPowerKw())).append(" kW로 집계되었다. ")
                .append("현재 부하가 24시간 지속된다고 가정한 일별 전력사용량은 ").append(round2(s.dailyEnergyKwh()))
                .append(" kWh이며, 일별 탄소배출량은 ").append(round4(s.dailyEmissionsTco2())).append(" tCO2eq이다.\n\n");

        markdown.append("### 4.2 관리 지표\n\n");
        markdown.append("| 지표 | 값 |\n|------|----|\n");
        markdown.append("| 측정 설비 수 | ").append(s.measuredEquipmentCount()).append(" |\n");
        markdown.append("| 평균 전압 | ").append(round2(s.avgVoltage())).append(" V |\n");
        markdown.append("| 평균 전류 | ").append(round2(s.avgCurrent())).append(" A |\n");
        markdown.append("| 평균 전력 | ").append(round2(s.avgPowerW())).append(" W |\n");
        markdown.append("| 최대 전력 | ").append(round2(s.maxPowerW())).append(" W |\n");
        markdown.append("| 일별 환산 전력사용량 | ").append(round2(s.dailyEnergyKwh())).append(" kWh |\n");
        markdown.append("| 월별 환산 전력사용량 | ").append(round2(s.monthlyEnergyKwh())).append(" kWh |\n");
        markdown.append("| 연간 환산 전력사용량 | ").append(round2(s.annualEnergyKwh())).append(" kWh |\n");
        markdown.append("| 전력 배출계수 | ").append(GRID_EMISSION_FACTOR_KG_PER_KWH / 1000.0).append(" tCO2eq/kWh |\n");
        markdown.append("| 연간 환산 탄소배출량 | ").append(round4(s.annualEmissionsTco2())).append(" tCO2eq |\n\n");

        markdown.append("## 5. 분석 결과\n\n");
        markdown.append("- 실시간 전압·전류 데이터를 기준으로 설비별 순간 전력을 계산하였다.\n");
        markdown.append("- 주요 배출 기여 설비는 ").append(topEmissionEquipmentList(s.energyRows())).append("이다.\n");
        markdown.append("- 전류·전압 급등 설비는 설비 부하와 온도 상승을 함께 확인한다.\n\n");
        appendEnergyEquipmentTable(markdown, s);
        markdown.append("## 6. 특이사항\n\n");
        markdown.append("본 보고서는 현재 버퍼의 최신값을 사용하므로, 장기 집계 DB가 축적되면 실제 누적 전력량 기준으로 보정할 수 있다.\n\n");
        appendApproval(markdown);
        return markdown.toString();
    }

    private void ensureLineRooms() {
        List<String> existingLineIds = chatRoomRepository.findByRoomTypeOrderByCreatedAtAsc("LINE")
                .stream()
                .map(ChatRoom::getLineId)
                .toList();
        for (ProductionLine line : lineRepository.findAll()) {
            if (existingLineIds.contains(line.getLineId())) {
                continue;
            }
            ChatRoom room = new ChatRoom();
            room.setLineId(line.getLineId());
            room.setRoomName(line.getLineName() + " 그룹 채팅");
            room.setRoomType("LINE");
            chatRoomRepository.save(room);
        }
    }

    private ChatRoom createDirectRoom(User requester, User target, String userA, String userB) {
        ChatRoom room = new ChatRoom();
        room.setLineId(target.getLineId() != null ? target.getLineId() : requester.getLineId());
        room.setRoomName(requester.getUserName() + " / " + target.getUserName());
        room.setRoomType("DIRECT");
        room.setUserAId(userA);
        room.setUserBId(userB);
        return chatRoomRepository.save(room);
    }

    private boolean canAccessRoom(User user, ChatRoom room) {
        if (role(user).equals("ADMIN")) return true;
        if ("DIRECT".equals(room.getRoomType())) {
            return user.getUserId().equals(room.getUserAId()) || user.getUserId().equals(room.getUserBId());
        }
        return user.getLineId() != null && user.getLineId().equals(room.getLineId());
    }

    private boolean canDirectMessage(User requester, User target) {
        if (role(requester).equals("ADMIN")) return true;
        return requester.getLineId() != null && requester.getLineId().equals(target.getLineId());
    }

    private User user(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    private LineGroupResponse.UserBrief brief(User user) {
        return new LineGroupResponse.UserBrief(
                user.getUserId(),
                user.getLoginId(),
                user.getUserName(),
                user.getRoleName(),
                user.getLineId()
        );
    }

    private String role(User user) {
        return user.getRoleName() == null ? "" : user.getRoleName().toUpperCase(Locale.ROOT);
    }

    private FactorySnapshot factorySnapshot() {
        List<LineResponse> lines = lineAggregationService.getLines("FACTORY-01");
        List<Equipment> equipments = equipmentRepository.findAll();
        List<Alarm> openAlarmRows = alarmRepository.findByFilters("OPEN", null, null, null);
        List<EquipmentEnergyRow> energyRows = equipments.stream()
                .map(this::equipmentEnergyRow)
                .toList();

        long equipmentTotal = lines.stream().mapToLong(LineResponse::equipmentTotal).sum();
        long running = lines.stream().mapToLong(LineResponse::equipmentRunning).sum();
        long alarm = lines.stream().mapToLong(LineResponse::equipmentAlarm).sum();
        long standby = lines.stream().mapToLong(line -> line.equipmentStandby() + line.equipmentMaintenance()).sum();
        double avgOee = lines.stream()
                .map(LineResponse::latestOee)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        List<EquipmentEnergyRow> measured = energyRows.stream()
                .filter(EquipmentEnergyRow::measured)
                .toList();
        double avgVoltage = average(measured.stream().map(EquipmentEnergyRow::voltage).filter(Objects::nonNull).toList());
        double avgCurrent = average(measured.stream().map(EquipmentEnergyRow::current).filter(Objects::nonNull).toList());
        double avgTemperature = average(measured.stream().map(EquipmentEnergyRow::temperature).filter(Objects::nonNull).toList());
        double maxTemperature = measured.stream().map(EquipmentEnergyRow::temperature).filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minTemperature = measured.stream().map(EquipmentEnergyRow::temperature).filter(Objects::nonNull).mapToDouble(Double::doubleValue).min().orElse(0.0);
        double totalPowerKw = measured.stream().mapToDouble(row -> row.powerW() / 1000.0).sum();
        double avgPowerW = measured.stream().mapToDouble(EquipmentEnergyRow::powerW).average().orElse(0.0);
        double maxPowerW = measured.stream().mapToDouble(EquipmentEnergyRow::powerW).max().orElse(0.0);
        double dailyEnergyKwh = totalPowerKw * 24.0;
        double monthlyEnergyKwh = dailyEnergyKwh * 30.0;
        double annualEnergyKwh = dailyEnergyKwh * 365.0;
        long openAlarmEquipments = openAlarmRows.stream().map(Alarm::getEquipmentCode).distinct().count();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        return new FactorySnapshot(
                today,
                today.withDayOfYear(1),
                lines,
                energyRows,
                equipmentTotal,
                running,
                alarm,
                standby,
                avgOee,
                openAlarmRows.size(),
                openAlarmEquipments,
                measured.size(),
                avgVoltage,
                avgCurrent,
                avgTemperature,
                maxTemperature,
                minTemperature,
                totalPowerKw,
                avgPowerW,
                maxPowerW,
                dailyEnergyKwh,
                monthlyEnergyKwh,
                annualEnergyKwh
        );
    }

    private EquipmentEnergyRow equipmentEnergyRow(Equipment equipment) {
        Double voltage = latestMetricValue(equipment.getEquipmentCode(), "sensor_voltage");
        Double current = latestMetricValue(equipment.getEquipmentCode(), "sensor_current");
        Double temperature = latestMetricValue(equipment.getEquipmentCode(), "sensor_temperature");
        double powerW = voltage != null && current != null ? voltage * current : 0.0;
        double dailyKwh = powerW / 1000.0 * 24.0;
        double annualKwh = dailyKwh * 365.0;
        double annualEmissionsTco2 = annualKwh * GRID_EMISSION_FACTOR_KG_PER_KWH / 1000.0;
        return new EquipmentEnergyRow(
                equipment.getEquipmentCode(),
                equipment.getEquipmentName(),
                equipment.getLocation(),
                equipment.getProcessType(),
                voltage,
                current,
                temperature,
                powerW,
                dailyKwh,
                annualKwh,
                annualEmissionsTco2,
                voltage != null || current != null || temperature != null
        );
    }

    private void appendDocumentInfo(
            StringBuilder markdown,
            String title,
            String reportType,
            LocalDate start,
            LocalDate end
    ) {
        markdown.append("## 1. 문서 정보\n\n");
        markdown.append("| 항목 | 값 |\n|------|----|\n");
        markdown.append("| 문서명 | ").append(title).append(" |\n");
        markdown.append("| 보고 구분 | ").append(reportType).append(" |\n");
        markdown.append("| 보고 기간 | ").append(start).append(" ~ ").append(end).append(" |\n");
        markdown.append("| 사업장명 | UECADA SmartFactory |\n");
        markdown.append("| 대상 구역 | LINE-01, LINE-02, LINE-03 |\n");
        markdown.append("| 작성일 | ").append(Instant.now()).append(" |\n");
        markdown.append("| 작성 부서 | 생산기술/ESG 관리 |\n");
        markdown.append("| 문서 버전 | v1.0-auto |\n\n");
    }

    private void appendLineStatus(StringBuilder markdown, List<LineResponse> lines) {
        markdown.append("## 2. 라인 현황\n\n");
        markdown.append("| 라인 | OEE | 전체 설비 | 가동 | 대기/보전 | 알람 |\n");
        markdown.append("|------|-----|-----------|------|-----------|------|\n");
        for (LineResponse line : lines) {
            markdown.append("| ").append(line.lineName()).append(" | ")
                    .append(line.latestOee() == null ? "-" : round1(line.latestOee())).append("% | ")
                    .append(line.equipmentTotal()).append(" | ")
                    .append(line.equipmentRunning()).append(" | ")
                    .append(line.equipmentStandby() + line.equipmentMaintenance()).append(" | ")
                    .append(line.equipmentAlarm()).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendHeatEquipmentTable(StringBuilder markdown, FactorySnapshot s) {
        markdown.append("## 6. 설비별 현황\n\n");
        markdown.append("| 설비 ID | 평균 온도(°C) | 최고 온도(°C) | 관심 이상 | 경고 이상 | 최종 경보 단계 |\n");
        markdown.append("|---------|---------------|---------------|-----------|-----------|----------------|\n");
        for (EquipmentEnergyRow row : topTemperatureRows(s.energyRows())) {
            double temperature = row.temperature() == null ? 0.0 : row.temperature();
            markdown.append("| ").append(row.equipmentCode()).append(" | ")
                    .append(row.temperature() == null ? "-" : round2(temperature)).append(" | ")
                    .append(row.temperature() == null ? "-" : round2(temperature)).append(" | ")
                    .append(temperature >= 30.0 ? "Y" : "N").append(" | ")
                    .append(temperature >= 35.0 ? "Y" : "N").append(" | ")
                    .append(heatLevel(temperature)).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendIntegratedEquipmentTable(StringBuilder markdown, FactorySnapshot s) {
        markdown.append("### 5.2 설비별 통합 현황\n\n");
        markdown.append("| 설비 ID | 평균 온도(°C) | 최고 온도(°C) | 평균 전압(V) | 평균 전류(A) | 연간 환산 전력(kWh) | 배출량(tCO2eq) | 경보 단계 |\n");
        markdown.append("|---------|---------------|---------------|--------------|--------------|---------------------|----------------|-----------|\n");
        for (EquipmentEnergyRow row : topEmissionRows(s.energyRows())) {
            double temperature = row.temperature() == null ? 0.0 : row.temperature();
            markdown.append("| ").append(row.equipmentCode()).append(" | ")
                    .append(row.temperature() == null ? "-" : round2(temperature)).append(" | ")
                    .append(row.temperature() == null ? "-" : round2(temperature)).append(" | ")
                    .append(row.voltage() == null ? "-" : round2(row.voltage())).append(" | ")
                    .append(row.current() == null ? "-" : round2(row.current())).append(" | ")
                    .append(round2(row.annualEnergyKwh())).append(" | ")
                    .append(round4(row.annualEmissionsTco2())).append(" | ")
                    .append(heatLevel(temperature)).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendEnergyEquipmentTable(StringBuilder markdown, FactorySnapshot s) {
        markdown.append("### 5.2 설비별 상세 현황\n\n");
        markdown.append("| 설비 ID | 평균 전압(V) | 평균 전류(A) | 평균 전력(W) | 최대 전력(W) | 연간 환산 전력(kWh) | 배출량(tCO2eq) |\n");
        markdown.append("|---------|--------------|--------------|--------------|--------------|---------------------|----------------|\n");
        for (EquipmentEnergyRow row : topEmissionRows(s.energyRows())) {
            markdown.append("| ").append(row.equipmentCode()).append(" | ")
                    .append(row.voltage() == null ? "-" : round2(row.voltage())).append(" | ")
                    .append(row.current() == null ? "-" : round2(row.current())).append(" | ")
                    .append(round2(row.powerW())).append(" | ")
                    .append(round2(row.powerW())).append(" | ")
                    .append(round2(row.annualEnergyKwh())).append(" | ")
                    .append(round4(row.annualEmissionsTco2())).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendRecommendation(StringBuilder markdown, FactorySnapshot s) {
        markdown.append("\n## 3. 권고\n\n");
        markdown.append("- OEE가 낮은 라인은 cycle_time 버퍼와 병목 공정을 우선 확인한다.\n");
        markdown.append("- OPEN 알람 ").append(s.openAlarms()).append("건은 raw window, FFT, 센서 스냅샷을 같은 시간대 기준으로 대조한다.\n");
        markdown.append("- ESG 관점에서는 전류/전압 급등 설비와 온도 상승 설비를 묶어 점검한다.\n");
    }

    private void appendApproval(StringBuilder markdown) {
        markdown.append("## 결재\n\n");
        markdown.append("| 작성 | 검토 | 승인 |\n|------|------|------|\n");
        markdown.append("| 자동 문서화 | 생산기술팀 | 공장 관리자 |\n");
    }

    private List<EquipmentEnergyRow> topTemperatureRows(List<EquipmentEnergyRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(row -> row.temperature() == null ? -1.0 : -row.temperature()))
                .limit(5)
                .toList();
    }

    private List<EquipmentEnergyRow> topEmissionRows(List<EquipmentEnergyRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(EquipmentEnergyRow::annualEmissionsTco2).reversed())
                .limit(5)
                .toList();
    }

    private String topEmissionEquipmentList(List<EquipmentEnergyRow> rows) {
        List<String> codes = topEmissionRows(rows).stream()
                .filter(row -> row.annualEmissionsTco2() > 0)
                .map(EquipmentEnergyRow::equipmentCode)
                .toList();
        return codes.isEmpty() ? "측정 대기" : String.join(", ", codes);
    }

    private String heatLevel(double temperature) {
        if (temperature >= 38.0) return "심각";
        if (temperature >= 35.0) return "경고";
        if (temperature >= 33.0) return "주의";
        if (temperature >= 30.0) return "관심";
        return "정상";
    }

    private List<Double> latestMetricValues(List<String> equipmentCodes, String metric) {
        return equipmentCodes.stream()
                .map(code -> latestMetricValue(code, metric))
                .filter(Objects::nonNull)
                .toList();
    }

    private Double latestMetricValue(String equipmentCode, String metric) {
        for (String key : SensorBufferKeys.lookupKeys(equipmentCode, metric)) {
            SensorBuffer buffer = sensorBufferRegistry.get(key);
            SensorFrame frame = buffer == null ? null : buffer.latest();
            if (frame != null) return frame.value();
        }
        return null;
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record EquipmentEnergyRow(
            String equipmentCode,
            String equipmentName,
            String lineId,
            String processType,
            Double voltage,
            Double current,
            Double temperature,
            double powerW,
            double dailyEnergyKwh,
            double annualEnergyKwh,
            double annualEmissionsTco2,
            boolean measured
    ) {
    }

    private record FactorySnapshot(
            LocalDate today,
            LocalDate yearStart,
            List<LineResponse> lines,
            List<EquipmentEnergyRow> energyRows,
            long equipmentTotal,
            long running,
            long alarm,
            long standby,
            double avgOee,
            long openAlarms,
            long openAlarmEquipments,
            long measuredEquipmentCount,
            double avgVoltage,
            double avgCurrent,
            double avgTemperature,
            double maxTemperature,
            double minTemperature,
            double totalPowerKw,
            double avgPowerW,
            double maxPowerW,
            double dailyEnergyKwh,
            double monthlyEnergyKwh,
            double annualEnergyKwh
    ) {
        long attentionHeatCount() {
            return heatCount(30.0);
        }

        long cautionHeatCount() {
            return heatCount(33.0);
        }

        long warningHeatCount() {
            return heatCount(35.0);
        }

        long dangerHeatCount() {
            return heatCount(38.0);
        }

        long heatCount(double threshold) {
            return energyRows.stream()
                    .map(EquipmentEnergyRow::temperature)
                    .filter(Objects::nonNull)
                    .filter(value -> value >= threshold)
                    .count();
        }

        double hourlyEmissionsKg() {
            return totalPowerKw * GRID_EMISSION_FACTOR_KG_PER_KWH;
        }

        double dailyEmissionsTco2() {
            return dailyEnergyKwh * GRID_EMISSION_FACTOR_KG_PER_KWH / 1000.0;
        }

        double monthlyEmissionsTco2() {
            return monthlyEnergyKwh * GRID_EMISSION_FACTOR_KG_PER_KWH / 1000.0;
        }

        double annualEmissionsTco2() {
            return annualEnergyKwh * GRID_EMISSION_FACTOR_KG_PER_KWH / 1000.0;
        }

        double emissionsPerEquipmentTco2() {
            return measuredEquipmentCount == 0 ? 0.0 : annualEmissionsTco2() / measuredEquipmentCount;
        }
    }
}
