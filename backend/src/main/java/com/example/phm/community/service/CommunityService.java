package com.example.phm.community.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
    public FactoryReportResponse factoryReport() {
        List<LineResponse> lines = lineAggregationService.getLines("FACTORY-01");
        long equipmentTotal = lines.stream().mapToLong(LineResponse::equipmentTotal).sum();
        long running = lines.stream().mapToLong(LineResponse::equipmentRunning).sum();
        long alarm = lines.stream().mapToLong(LineResponse::equipmentAlarm).sum();
        long standby = lines.stream().mapToLong(LineResponse::equipmentStandby).sum();
        double avgOee = lines.stream()
                .map(LineResponse::latestOee)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        EnergySnapshot energy = energySnapshot();
        long openAlarms = alarmRepository.findByFilters("OPEN", null, null, null).size();

        StringBuilder markdown = new StringBuilder();
        markdown.append("# UECADA 공장 현황 자동 문서\n\n");
        markdown.append("- 생성 시각: ").append(Instant.now()).append("\n");
        markdown.append("- 전체 설비: ").append(equipmentTotal).append("대\n");
        markdown.append("- 가동: ").append(running).append("대, 대기/보전: ").append(standby).append("대, 이상/알람: ").append(alarm).append("대\n");
        markdown.append("- 평균 OEE: ").append(round1(avgOee)).append("%\n");
        markdown.append("- OPEN 알람: ").append(openAlarms).append("건\n\n");

        markdown.append("## 라인 현황\n\n");
        for (LineResponse line : lines) {
            markdown.append("- ").append(line.lineName()).append(" (").append(line.lineId()).append("): OEE ")
                    .append(line.latestOee() == null ? "-" : round1(line.latestOee())).append("%, 설비 ")
                    .append(line.equipmentTotal()).append("대, 가동 ").append(line.equipmentRunning())
                    .append("대, 알람 ").append(line.equipmentAlarm()).append("대\n");
        }

        markdown.append("\n## 생산률과 설비 이상\n\n");
        markdown.append("- 생산률은 실시간 설비 상태와 라인별 OEE를 기준으로 산정합니다.\n");
        markdown.append("- 이상 설비는 실시간 AI 분석의 warning/danger 상태와 DB의 OPEN 알람을 함께 확인합니다.\n\n");

        markdown.append("## ESG 대응 데이터\n\n");
        markdown.append("- 최신 전압 평균: ").append(round2(energy.avgVoltage())).append(" V\n");
        markdown.append("- 최신 전류 평균: ").append(round2(energy.avgCurrent())).append(" A\n");
        markdown.append("- 추정 전력: ").append(round2(energy.estimatedKw())).append(" kW\n");
        markdown.append("- 추정 탄소배출량: ").append(round2(energy.estimatedKgCo2PerHour()))
                .append(" kgCO2e/h (계수 ").append(GRID_EMISSION_FACTOR_KG_PER_KWH).append(" kgCO2e/kWh)\n");
        markdown.append("- 최신 온도 평균: ").append(round2(energy.avgTemperature()))
                .append(" °C. 온도 상승은 에너지 손실과 설비 부하 증가의 징후로 함께 추적합니다.\n\n");

        markdown.append("## 권고\n\n");
        markdown.append("- OEE가 낮은 라인은 cycle_time 버퍼와 병목 공정을 우선 확인합니다.\n");
        markdown.append("- 알람 설비는 raw window, FFT, AI 예측 후보, 센서 스냅샷을 같은 시간대 기준으로 대조합니다.\n");
        markdown.append("- ESG 관점에서는 전류/전압 급등 설비와 온도 상승 설비를 묶어 점검합니다.\n");

        return new FactoryReportResponse(Instant.now(), "UECADA 공장 현황 자동 문서", markdown.toString());
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

    private EnergySnapshot energySnapshot() {
        List<String> codes = equipmentRepository.findAll().stream().map(e -> e.getEquipmentCode()).toList();
        List<Double> voltages = latestMetricValues(codes, "sensor_voltage");
        List<Double> currents = latestMetricValues(codes, "sensor_current");
        List<Double> temperatures = latestMetricValues(codes, "sensor_temperature");
        double avgVoltage = average(voltages);
        double avgCurrent = average(currents);
        double estimatedKw = avgVoltage * avgCurrent * Math.max(1, Math.min(codes.size(), currents.size())) / 1000.0;
        return new EnergySnapshot(
                avgVoltage,
                avgCurrent,
                average(temperatures),
                estimatedKw,
                estimatedKw * GRID_EMISSION_FACTOR_KG_PER_KWH
        );
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

    private record EnergySnapshot(
            double avgVoltage,
            double avgCurrent,
            double avgTemperature,
            double estimatedKw,
            double estimatedKgCo2PerHour
    ) {
    }
}
