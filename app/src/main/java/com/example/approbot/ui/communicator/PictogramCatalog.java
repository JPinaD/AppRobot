package com.example.approbot.ui.communicator;

import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo estático de pictogramas ARASAAC para el comunicador bidireccional.
 * Los IDs coinciden con los nombres de los drawables en res/drawable/.
 */
public class PictogramCatalog {

    public enum Category {
        NEEDS("Necesidades"),
        EMOTIONS("Emociones"),
        ACTIONS("Acciones"),
        PEOPLE("Personas"),
        PLACES("Lugares"),
        RESPONSES("Respuestas");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public static class PictogramItem {
        public final String id;
        public final String label;
        public final Category category;

        public PictogramItem(String id, String label, Category category) {
            this.id = id;
            this.label = label;
            this.category = category;
        }
    }

    private static final List<PictogramItem> ALL_PICTOGRAMS = new ArrayList<>();

    static {
        // NEEDS
        ALL_PICTOGRAMS.add(new PictogramItem("picto_needs_water", "Agua", Category.NEEDS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_needs_food", "Comida", Category.NEEDS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_needs_bathroom", "Baño", Category.NEEDS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_needs_help", "Ayuda", Category.NEEDS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_needs_rest", "Descanso", Category.NEEDS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_needs_play", "Jugar", Category.NEEDS));

        // EMOTIONS
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_happy", "Alegría", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_sad", "Tristeza", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_angry", "Enfado", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_surprised", "Sorpresa", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_scared", "Miedo", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_disgusted", "Asco", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_calm", "Calma", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_shy", "Vergüenza", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_bored", "Aburrimiento", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_tired", "Cansancio", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_excited", "Emoción", Category.EMOTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("emotion_terror", "Terror", Category.EMOTIONS));

        // ACTIONS (note: some drawables use double underscore in actual files)
        ALL_PICTOGRAMS.add(new PictogramItem("picto_actions_listen", "Escuchar", Category.ACTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_actions_look", "Mirar", Category.ACTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_actions__wait", "Esperar", Category.ACTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_actions__sit", "Sentarse", Category.ACTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_actions_come", "Ven", Category.ACTIONS));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_actions_stop", "Para", Category.ACTIONS));

        // PEOPLE
        ALL_PICTOGRAMS.add(new PictogramItem("picto_people_teacher", "Profesor", Category.PEOPLE));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_people_friend", "Amigo", Category.PEOPLE));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_people_family", "Familia", Category.PEOPLE));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_people_me", "Yo", Category.PEOPLE));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_people_you", "Tú", Category.PEOPLE));

        // PLACES (note: actual drawable filenames have typos - match them exactly)
        ALL_PICTOGRAMS.add(new PictogramItem("picto_places_clasroom", "Clase", Category.PLACES));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_places_playgound", "Patio", Category.PLACES));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_places_home", "Casa", Category.PLACES));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_places_bathroom", "Baño", Category.PLACES));

        // RESPONSES
        ALL_PICTOGRAMS.add(new PictogramItem("picto_responses_yes", "Sí", Category.RESPONSES));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_responses_no", "No", Category.RESPONSES));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_responses_more", "Más", Category.RESPONSES));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_responses_finished", "Terminado", Category.RESPONSES));
        ALL_PICTOGRAMS.add(new PictogramItem("picto_responses_again", "Otra vez", Category.RESPONSES));
    }

    public static List<PictogramItem> getAll() {
        return new ArrayList<>(ALL_PICTOGRAMS);
    }

    public static List<PictogramItem> getByCategory(Category category) {
        List<PictogramItem> result = new ArrayList<>();
        for (PictogramItem item : ALL_PICTOGRAMS) {
            if (item.category == category) result.add(item);
        }
        return result;
    }

    /** Find a pictogram by its ID. Returns null if not found. */
    public static PictogramItem findById(String id) {
        for (PictogramItem item : ALL_PICTOGRAMS) {
            if (item.id.equals(id)) return item;
        }
        return null;
    }
}
