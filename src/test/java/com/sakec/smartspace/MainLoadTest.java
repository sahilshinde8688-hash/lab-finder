package com.sakec.smartspace;

import com.sakec.smartspace.dao.UserDao;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Session;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.Test;

public class MainLoadTest {
    @Test
    void testMainFxmlLoad() throws Exception {
        User user = new UserDao().findByEmail("demo.coordinator2@sakec.ac.in");
        Session.start(user);

        CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];

        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }

        try {
            Platform.runLater(() -> {
                try {
                    URL url = getClass().getResource("/fxml/Main.fxml");
                    System.out.println("Loading FXML from: " + url);
                    Object root = FXMLLoader.load(url);
                    System.out.println("LOADED ROOT: " + root);
                } catch (Throwable t) {
                    error[0] = t;
                    t.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });

            latch.await(10, TimeUnit.SECONDS);
            if (error[0] != null) {
                throw new RuntimeException("FXML load failed: " + error[0].getMessage(), error[0]);
            }
        } finally {
            Session.clear();
        }
    }
}
