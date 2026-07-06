package com.example.approbot.data.model;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO ligero del perfil del alumno recibido en el payload de ACTIVITY_START.
 * Sin persistencia local — solo vive durante la actividad.
 */
public class StudentProfile {

    private static final String TAG = "StudentProfile";

    /** Tipo de calma por defecto si no se configura en el perfil. */
    public static final String DEFAULT_CALM_TYPE = "calm_breathing";

    public final String id;
    public final String name;
    public final List<String> excludedColors;   // hex en mayúsculas, ej: "#C8E6C9"
    public final String backgroundSoundResName; // nombre de recurso raw, o null
    public final String calmType;               // "calm_breathing", "calm_colors", "calm_music", "calm_counting"
    public final String jointAttentionLevel; // nivel de atención conjunta

    public StudentProfile(String id, String name,
                          List<String> excludedColors, String backgroundSoundResName,
                          String calmType, String jointAttentionLevel) {
        this.id = id;
        this.name = name;
        this.excludedColors = excludedColors != null ? excludedColors : new ArrayList<>();
        this.backgroundSoundResName = backgroundSoundResName;
        this.calmType = (calmType != null && !calmType.isEmpty()) ? calmType : DEFAULT_CALM_TYPE;
        this.jointAttentionLevel = jointAttentionLevel;
    }

    /**
     * Parsea el objeto JSON del perfil. Devuelve null si el JSON es inválido.
     */
    public static StudentProfile fromJson(JSONObject obj) {
        if (obj == null) return null;
        try {
            String id   = obj.optString("id", "");
            String name = obj.optString("name", "");

            List<String> colors = new ArrayList<>();
            JSONArray arr = obj.optJSONArray("excludedColors");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    colors.add(arr.getString(i).toUpperCase());
                }
            }

            String sound = obj.has("backgroundSoundResName")
                    ? obj.optString("backgroundSoundResName", null)
                    : null;

            String calmType = obj.optString("calmType", DEFAULT_CALM_TYPE);

            String jointAttention = obj.optString("jointAttentionLevel", null);

            return new StudentProfile(id, name, colors, sound, calmType, jointAttention);
        } catch (JSONException e) {
            Log.w(TAG, "Error parseando StudentProfile: " + e.getMessage());
            return null;
        }
    }
}
