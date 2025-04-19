module org.openjfx {
    requires transitive javafx.controls;
    requires javafx.fxml;
    requires bluecove;
    requires javafx.graphics;
    requires javafx.base;
    requires java.base;
    requires jdk.compiler;
    requires jdk.javadoc;
    requires opencsv;

    opens org.openjfx to javafx.fxml;
    exports org.openjfx;
}
