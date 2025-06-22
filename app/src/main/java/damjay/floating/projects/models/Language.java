package damjay.floating.projects.models;

public class Language {
    private String code;
    private String name;

    public Language(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Language[] getSupportedLanguages() {
        return new Language[]{
            new Language("auto", "Auto Detect"),
            new Language("vi", "Vietnamese"),
            new Language("en", "English"),
            new Language("zh", "Chinese"),
            new Language("ja", "Japanese"),
            new Language("ko", "Korean"),
            new Language("es", "Spanish"),
            new Language("fr", "French"),
            new Language("de", "German"),
            new Language("ru", "Russian"),
            new Language("ar", "Arabic")
        };
    }
}