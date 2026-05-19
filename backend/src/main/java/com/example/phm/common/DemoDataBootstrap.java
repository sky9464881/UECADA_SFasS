package com.example.phm.common;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.auth.entity.User;
import com.example.phm.auth.repository.UserRepository;
import com.example.phm.auth.service.AuthService;
import com.example.phm.community.entity.BoardPost;
import com.example.phm.community.entity.ChatMessage;
import com.example.phm.community.entity.ChatRoom;
import com.example.phm.community.repository.BoardPostRepository;
import com.example.phm.community.repository.ChatMessageRepository;
import com.example.phm.community.repository.ChatRoomRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataBootstrap implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "secret";
    private static final String DEFAULT_SECURITY_QUESTION = "초기 보안 답변은?";
    private static final String DEFAULT_SECURITY_ANSWER = "secret";

    private final UserRepository userRepository;
    private final BoardPostRepository boardPostRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public DemoDataBootstrap(
            UserRepository userRepository,
            BoardPostRepository boardPostRepository,
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.userRepository = userRepository;
        this.boardPostRepository = boardPostRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<DefaultUser> users = List.of(
                new DefaultUser("U001", null, "admin", "관리자", "admin@uecada.com", "ADMIN"),
                new DefaultUser("U101", "LINE-01", "line01_manager", "1라인 관리자", "line01.manager@uecada.com", "MANAGER"),
                new DefaultUser("U102", "LINE-01", "line01_operator", "1라인 작업자", "line01.operator@uecada.com", "OPERATOR"),
                new DefaultUser("U201", "LINE-02", "line02_manager", "2라인 관리자", "line02.manager@uecada.com", "MANAGER"),
                new DefaultUser("U202", "LINE-02", "line02_operator", "2라인 작업자", "line02.operator@uecada.com", "OPERATOR"),
                new DefaultUser("U301", "LINE-03", "line03_manager", "3라인 관리자", "line03.manager@uecada.com", "MANAGER"),
                new DefaultUser("U302", "LINE-03", "line03_operator", "3라인 작업자", "line03.operator@uecada.com", "OPERATOR")
        );
        users.forEach(this::upsert);
        seedBoardPosts();
        seedChatRoomsAndMessages();
    }

    private void upsert(DefaultUser defaults) {
        User user = userRepository.findById(defaults.userId()).orElseGet(User::new);
        boolean isNew = user.getUserId() == null;
        user.setUserId(defaults.userId());
        user.setLineId(defaults.lineId());
        user.setLoginId(defaults.loginId());
        user.setUserName(defaults.userName());
        user.setEmail(defaults.email());
        user.setRoleName(defaults.roleName());
        user.setSecurityQuestion(DEFAULT_SECURITY_QUESTION);
        if (isNew || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(AuthService.encodePassword(DEFAULT_PASSWORD));
        }
        if (isNew || user.getSecurityAnswerHash() == null || user.getSecurityAnswerHash().isBlank()) {
            user.setSecurityAnswerHash(AuthService.encodeSecurityAnswer(DEFAULT_SECURITY_ANSWER));
        }
        userRepository.save(user);
    }

    private void seedBoardPosts() {
        List<DefaultPost> posts = List.of(
                new DefaultPost("NOTICE", null, true, "6월 정기 안전점검 및 설비 PM 일정 안내",
                        "6월 3일 09:00부터 라인별 정기 안전점검과 예방보전 점검을 진행합니다. 작업자는 점검 시간 전까지 설비 주변 자재를 정리해 주세요."),
                new DefaultPost("NOTICE", "LINE-01", true, "A라인 주조기 냉각수 점검 시간 공지",
                        "LINE-01 주조기 냉각수 유량 점검이 14:00~14:30 사이 진행됩니다. 해당 시간에는 관리자 지시에 따라 설비 상태를 확인해 주세요."),
                new DefaultPost("NOTICE", null, true, "ESG 대응 전력 사용 데이터 제출 안내",
                        "전력 사용량 및 탄소 배출 자동 보고서 검토를 위해 금일 17:00까지 이상 전류 발생 설비의 조치 내용을 등록해 주세요."),
                new DefaultPost("QNA", "LINE-02", false, "세척기 농도 보정 후에도 알람이 반복될 때 확인 순서가 궁금합니다",
                        "세척 농도 보정 후 10분 내 동일 알람이 재발하면 농도 센서 세척 상태, 보충액 투입량, 순환 펌프 압력을 순서대로 확인해 주세요."),
                new DefaultPost("QNA", "LINE-03", false, "검사기 NG 증가 시 우선 봐야 하는 지표는 무엇인가요?",
                        "bore_dimension, hole_dimension 편차와 검사기 온도를 먼저 확인하고, 직전 조립기 press_force 이력도 함께 비교해 주세요."),
                new DefaultPost("QNA", null, false, "알람 발생 시 raw window 저장 여부는 어디서 확인하나요?",
                        "알람 이력 상세 화면에서 저장된 raw window와 FFT 분석값을 함께 확인할 수 있습니다. 저장은 알람 발생 시 자동 처리됩니다."),
                new DefaultPost("HANDOVER", null, false, "라인별 교대 인수인계 체크리스트",
                        "가동률, 미처리 알람, 공구 사용률, 세척액 농도, 검사 NG 추이를 교대 전 확인하고 특이사항은 채팅방에 공유해 주세요."),
                new DefaultPost("HANDOVER", "LINE-01", false, "주조기 냉각수 유량 점검 절차",
                        "냉각수 유량이 기준 이하로 내려가면 밸브 개도, 펌프 압력, 필터 막힘 여부를 확인한 뒤 조치 내용을 설비 상세에 기록합니다."),
                new DefaultPost("HANDOVER", null, false, "자동 문서화 보고서 저장 위치 안내",
                        "커뮤니티 자동 문서화에서 생성한 보고서는 Markdown으로 저장할 수 있으며, ESG 운영 회의 자료로 활용합니다.")
        );
        posts.forEach(this::insertPostIfMissing);
    }

    private void seedChatRoomsAndMessages() {
        ChatRoom line01 = lineRoom("LINE-01", "LINE 1 그룹 채팅");
        ChatRoom line02 = lineRoom("LINE-02", "LINE 2 그룹 채팅");
        ChatRoom line03 = lineRoom("LINE-03", "LINE 3 그룹 채팅");
        ChatRoom direct01 = directRoom("U001", "U102", "관리자 / 1라인 작업자", "LINE-01");
        ChatRoom direct02 = directRoom("U001", "U201", "관리자 / 2라인 관리자", "LINE-02");
        ChatRoom direct03 = directRoom("U001", "U302", "관리자 / 3라인 작업자", "LINE-03");

        LocalDateTime base = LocalDateTime.now().minusMinutes(40);
        message(line01, "U101", "A라인 주조기 냉각수 유량 확인했습니다. 14시 점검 전까지 정상 범위 유지 중입니다.", base.plusMinutes(1));
        message(line01, "U102", "주조기 2번 온도 상승 추이가 있어 10분 간격으로 한 번 더 확인하겠습니다.", base.plusMinutes(4));
        message(line01, "U101", "확인 후 설비 상세 알람 메모에 남겨 주세요.", base.plusMinutes(7));
        message(line02, "U201", "세척기 농도 보정 완료했습니다. 다음 윈도우에서 알람 재발 여부 보겠습니다.", base.plusMinutes(10));
        message(line02, "U202", "가공기 공구 사용률 82%입니다. 교체 준비해두겠습니다.", base.plusMinutes(13));
        message(line03, "U301", "검사 NG가 2건 발생했습니다. 조립기 press_force 이력 같이 확인하겠습니다.", base.plusMinutes(16));
        message(line03, "U302", "검사기 치수 편차 화면 캡처해서 자료실에 올려두겠습니다.", base.plusMinutes(19));
        message(direct01, "U102", "관리자님, LINE-01 주조기 온도가 순간적으로 34도를 넘었습니다.", base.plusMinutes(22));
        message(direct01, "U001", "알람으로 전환되면 raw window 저장 여부 확인하고 바로 공유해 주세요.", base.plusMinutes(24));
        message(direct02, "U201", "2라인 세척기 농도 보정 후 현재 2회 연속 정상입니다.", base.plusMinutes(27));
        message(direct02, "U001", "좋습니다. 30분 뒤 한 번 더 확인하고 Q&A에 조치 순서도 남겨주세요.", base.plusMinutes(30));
        message(direct03, "U302", "3라인 검사 NG 원인 확인 중입니다. hole_dimension 쪽 편차가 큽니다.", base.plusMinutes(33));
    }

    private void insertPostIfMissing(DefaultPost defaults) {
        if (boardPostRepository.existsByTitleAndDeletedFalse(defaults.title())) {
            return;
        }
        BoardPost post = new BoardPost();
        post.setAuthorUserId("U001");
        post.setCategory(defaults.category());
        post.setTargetLineId(defaults.targetLineId());
        post.setNotice(defaults.notice());
        post.setTitle(defaults.title());
        post.setContent(defaults.content());
        boardPostRepository.save(post);
    }

    private ChatRoom lineRoom(String lineId, String roomName) {
        return chatRoomRepository.findByRoomTypeOrderByCreatedAtAsc("LINE")
                .stream()
                .filter(room -> lineId.equals(room.getLineId()))
                .findFirst()
                .orElseGet(() -> {
                    ChatRoom room = new ChatRoom();
                    room.setLineId(lineId);
                    room.setRoomName(roomName);
                    room.setRoomType("LINE");
                    return chatRoomRepository.save(room);
                });
    }

    private ChatRoom directRoom(String userA, String userB, String roomName, String lineId) {
        String first = userA.compareTo(userB) <= 0 ? userA : userB;
        String second = first.equals(userA) ? userB : userA;
        return chatRoomRepository.findByRoomTypeAndUserAIdAndUserBId("DIRECT", first, second)
                .orElseGet(() -> {
                    ChatRoom room = new ChatRoom();
                    room.setLineId(lineId);
                    room.setRoomName(roomName);
                    room.setRoomType("DIRECT");
                    room.setUserAId(first);
                    room.setUserBId(second);
                    return chatRoomRepository.save(room);
                });
    }

    private void message(ChatRoom room, String senderUserId, String content, LocalDateTime sentAt) {
        if (chatMessageRepository.existsByMessageContentAndDeletedFalse(content)) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setChatRoomId(room.getChatRoomId());
        message.setSenderUserId(senderUserId);
        message.setMessageContent(content);
        message.setSentAt(sentAt);
        chatMessageRepository.save(message);
    }

    private record DefaultUser(
            String userId,
            String lineId,
            String loginId,
            String userName,
            String email,
            String roleName
    ) {
    }

    private record DefaultPost(
            String category,
            String targetLineId,
            boolean notice,
            String title,
            String content
    ) {
    }
}
