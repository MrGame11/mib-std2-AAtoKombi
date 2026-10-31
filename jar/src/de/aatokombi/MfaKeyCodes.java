package de.aatokombi;

/** Direct DSI codes, resolved from the running firmware; no guessed A/B or track-key aliases. */
public final class MfaKeyCodes {
    final int keyboard;
    final int keyboard3gp;
    final int up;
    final int down;
    final int pressed;
    final boolean valid;

    MfaKeyCodes(int keyboard, int keyboard3gp, int up, int down, int pressed, int[] excluded) {
        this.keyboard = keyboard;
        this.keyboard3gp = keyboard3gp;
        this.up = up;
        this.down = down;
        this.pressed = pressed;
        boolean ok = keyboard > 0 && keyboard3gp > 0 && up > 0 && down > 0
                && up != down && pressed > 0;
        for (int i = 0; i < excluded.length; i++) {
            if (up == excluded[i] || down == excluded[i]) ok = false;
        }
        valid = ok;
    }

    static MfaKeyCodes fromFirmware() throws Exception {
        Class type = Class.forName("org.dsi.ifc.keypanel.Constants");
        String[] names = new String[]{"KEY_NEXT", "KEY_PREV", "KEY_MFW_ARROW_A_UP",
                "KEY_MFW_ARROW_A_DOWN", "KEY_MFW_ARROW_B_UP", "KEY_MFW_ARROW_B_DOWN",
                "KEY_MFW_ARROW_LEFT", "KEY_MFW_ARROW_RIGHT", "KEY_MFW_VOLUME_UP",
                "KEY_MFW_VOLUME_DOWN", "KEY_MFW_PTT_ON"};
        int[] excluded = new int[names.length];
        for (int i = 0; i < names.length; i++) excluded[i] = value(type, names[i]);
        return new MfaKeyCodes(value(type, "KBD_MFW"), value(type, "KBD_MFW_3GP"),
                value(type, "KEY_MFW_UP"), value(type, "KEY_MFW_DOWN"),
                value(type, "KST_PRESSED"), excluded);
    }

    static int value(Class type, String name) throws Exception {
        // JXE conversions may lack ConstantValue attributes. Read the real runtime fields.
        return type.getField(name).getInt(null);
    }

    boolean isWheel(int keyboardId) {
        return keyboardId == keyboard || keyboardId == keyboard3gp;
    }

    /** -1 = UP/navigation; +1 = DOWN/music; 0 = leave untouched. */
    int direction(int keyboardId, int key, int state, int validity) {
        if (!valid || validity != 1 || !isWheel(keyboardId) || state != pressed) return 0;
        return key == up ? -1 : (key == down ? 1 : 0);
    }

    public String toString() {
        return "kbd=" + keyboard + "/" + keyboard3gp + " UP=" + up + " DOWN=" + down
                + " pressed=" + pressed + " valid=" + valid;
    }
}
