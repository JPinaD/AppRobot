package com.example.approbot.data.model;

public class LocalStudentProfile {
    public String id;
    public String name;
    public String description;
    public boolean usePictograms;
    public boolean useAudio;

    public LocalStudentProfile(String id, String name, String description, boolean usePictograms, boolean useAudio) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.usePictograms = usePictograms;
        this.useAudio = useAudio;
    }
}
