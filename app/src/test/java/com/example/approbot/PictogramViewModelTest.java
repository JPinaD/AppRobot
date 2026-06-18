package com.example.approbot;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests unitarios para la lógica de selección de pictogramas.
 * Replica la lógica de PictogramViewModel sin depender del framework Android.
 */
public class PictogramViewModelTest {

    /**
     * Clase mínima que replica la lógica de selección de PictogramViewModel.
     */
    static class PictogramSelectionLogic {
        private boolean selectionInProgress = false;
        private int selectionCount = 0;

        /** Devuelve true si la selección fue procesada, false si ya había una en curso. */
        boolean onPictogramSelected() {
            if (selectionInProgress) return false;
            selectionInProgress = true;
            selectionCount++;
            return true;
        }

        void reset() {
            selectionInProgress = false;
        }

        int getSelectionCount() { return selectionCount; }
        boolean isSelectionInProgress() { return selectionInProgress; }
    }

    @Test
    public void primeraSeleccion_seProcesa() {
        PictogramSelectionLogic logic = new PictogramSelectionLogic();

        boolean processed = logic.onPictogramSelected();

        assertTrue(processed);
        assertEquals(1, logic.getSelectionCount());
    }

    @Test
    public void segundaSeleccion_mientrasHayUnaEnCurso_seIgnora() {
        PictogramSelectionLogic logic = new PictogramSelectionLogic();
        logic.onPictogramSelected(); // primera selección

        boolean processed = logic.onPictogramSelected(); // segunda mientras hay una en curso

        assertFalse(processed);
        assertEquals(1, logic.getSelectionCount()); // sigue siendo 1
    }

    @Test
    public void seleccionTrasReset_seProcesa() {
        PictogramSelectionLogic logic = new PictogramSelectionLogic();
        logic.onPictogramSelected();
        logic.reset();

        boolean processed = logic.onPictogramSelected();

        assertTrue(processed);
        assertEquals(2, logic.getSelectionCount());
    }
}
