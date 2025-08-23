package com.ggm.ggmsurvival.enums;

/**
 * 직업 타입 열거형
 * 플레이어가 선택할 수 있는 직업들을 정의
 */
public enum JobType {
    NONE("§7없음", "§7"),
    TANK("§9탱커", "§9"),
    WARRIOR("§c검사", "§c"),
    ARCHER("§e궁수", "§e");

    private final String displayName;
    private final String color;

    JobType(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    /**
     * 표시용 이름 반환
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 색상 코드 반환
     */
    public String getColor() {
        return color;
    }

    /**
     * 색상이 포함된 표시 이름 반환
     */
    public String getColoredName() {
        return color + displayName;
    }

    /**
     * 문자열로부터 JobType 반환 (안전한 방법)
     */
    public static JobType fromString(String str) {
        if (str == null || str.isEmpty()) {
            return NONE;
        }

        try {
            return JobType.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /**
     * 한글 이름으로부터 JobType 반환
     */
    public static JobType fromDisplayName(String displayName) {
        for (JobType type : values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * 직업 설명 반환
     */
    public String getDescription() {
        switch (this) {
            case TANK:
                return "§7높은 방어력과 체력을 가진 전사입니다.";
            case WARRIOR:
                return "§7강력한 근접 공격력을 가진 전사입니다.";
            case ARCHER:
                return "§7원거리 공격과 기동성이 뛰어난 궁수입니다.";
            default:
                return "§7직업을 선택해주세요.";
        }
    }

    /**
     * 직업별 주요 능력 반환
     */
    public String[] getMainAbilities() {
        switch (this) {
            case TANK:
                return new String[]{
                        "§7• 흉갑 착용 시 체력 증가",
                        "§7• 방패 사용 시 체력 회복",
                        "§7• 높은 방어력과 생존력"
                };
            case WARRIOR:
                return new String[]{
                        "§7• 검 사용 시 공격력 증가",
                        "§7• 크리티컬 확률 증가",
                        "§7• 강력한 근접 전투"
                };
            case ARCHER:
                return new String[]{
                        "§7• 활 사용 시 공격력 증가",
                        "§7• 가죽부츠 착용 시 이동속도 증가",
                        "§7• 장거리 정밀 공격"
                };
            default:
                return new String[]{"§7직업을 선택하면 능력이 표시됩니다."};
        }
    }
}