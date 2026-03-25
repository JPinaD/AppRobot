package com.example.approbot.util;

import com.example.approbot.data.model.LocalStudentProfile;

import java.util.ArrayList;
import java.util.List;

public class MockRobotDataProvider {

    public static List<LocalStudentProfile> getMockProfiles() {
        List<LocalStudentProfile> profiles = new ArrayList<>();

        profiles.add(new LocalStudentProfile(
                "p1",
                "Mario",
                "Apoyo visual alto y respuestas simples",
                true,
                false
        ));

        profiles.add(new LocalStudentProfile(
                "p2",
                "Lucía",
                "Refuerzo sonoro y pictogramas",
                true,
                true
        ));

        profiles.add(new LocalStudentProfile(
                "p3",
                "Diego",
                "Baja estimulación visual y tareas cortas",
                false,
                false
        ));

        return profiles;
    }
}