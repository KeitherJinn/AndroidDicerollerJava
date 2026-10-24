package com.example.dicerollerjava;

/** Immutable ARGB color roles shared by all native Views. */
public final class ThemeColors {
    public final int primary, onPrimary, primaryContainer, onPrimaryContainer;
    public final int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
    public final int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
    public final int background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant;
    public final int outline, outlineVariant, inverseSurface, inverseOnSurface;
    public final int error, onError, errorContainer, onErrorContainer, scrim, surfaceTint;
    public final int surfaceDim, surfaceBright, surfaceContainerLowest, surfaceContainerLow;
    public final int surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;

    /** Accent arrays are color/onColor/container/onContainer; surfaces follow the named roles below. */
    ThemeColors(int[] primaryRoles, int[] secondaryRoles, int[] tertiaryRoles,
                int[] surfaces, boolean dark) {
        this(primaryRoles, secondaryRoles, tertiaryRoles, surfaces,
                dark ? new int[] {0xFFF2B8B5, 0xFF601410, 0xFF8C1D18, 0xFFF9DEDC}
                     : new int[] {0xFFB3261E, 0xFFFFFFFF, 0xFFF9DEDC, 0xFF410E0B});
    }

    ThemeColors(int[] p, int[] s, int[] t, int[] n, int[] e) {
        primary = p[0]; onPrimary = p[1]; primaryContainer = p[2]; onPrimaryContainer = p[3];
        secondary = s[0]; onSecondary = s[1]; secondaryContainer = s[2]; onSecondaryContainer = s[3];
        tertiary = t[0]; onTertiary = t[1]; tertiaryContainer = t[2]; onTertiaryContainer = t[3];
        surface = n[0]; onSurface = n[1]; surfaceVariant = n[2]; onSurfaceVariant = n[3];
        background = surface; onBackground = onSurface;
        outline = n[4]; outlineVariant = n[5]; inverseSurface = n[6]; inverseOnSurface = n[7];
        surfaceDim = n[8]; surfaceBright = n[9]; surfaceContainerLowest = n[10];
        surfaceContainerLow = n[11]; surfaceContainer = n[12]; surfaceContainerHigh = n[13];
        surfaceContainerHighest = n[14];
        error = e[0]; onError = e[1]; errorContainer = e[2]; onErrorContainer = e[3];
        scrim = 0xFF000000; surfaceTint = primary;
    }
    static ThemeColors violet(boolean dark) {
        return dark ? new ThemeColors(new int[] {0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF}, new int[] {0xFFCCC2DC, 0xFF332D41, 0xFF4A4458, 0xFFE8DEF8}, new int[] {0xFFEFB8C8, 0xFF492532, 0xFF633B48, 0xFFFFD8E4}, new int[] {0xFF141218, 0xFFE6E0E9, 0xFF49454F, 0xFFCAC4D0, 0xFF938F99, 0xFF49454F, 0xFFE6E0E9, 0xFF313033, 0xFF141218, 0xFF3B383E, 0xFF0F0D13, 0xFF1D1B20, 0xFF211F26, 0xFF2B2930, 0xFF36343B}, true)
                : new ThemeColors(new int[] {0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D}, new int[] {0xFF625B71, 0xFFFFFFFF, 0xFFE8DEF8, 0xFF1D192B}, new int[] {0xFF7D5260, 0xFFFFFFFF, 0xFFFFD8E4, 0xFF31111D}, new int[] {0xFFFFFBFE, 0xFF1C1B1F, 0xFFE7E0EC, 0xFF49454F, 0xFF79747E, 0xFFCAC4D0, 0xFF313033, 0xFFF4EFF4, 0xFFDED8E1, 0xFFFFFBFE, 0xFFFFFFFF, 0xFFF7F2FA, 0xFFF3EDF7, 0xFFECE6F0, 0xFFE6E0E9}, false);
    }

    static ThemeColors ocean(boolean dark) {
        return dark ? new ThemeColors(new int[] {0xFF9ECAFF, 0xFF003258, 0xFF00497D, 0xFFD1E4FF}, new int[] {0xFFBBC7DB, 0xFF253140, 0xFF3B4858, 0xFFD7E3F7}, new int[] {0xFFD6BEE4, 0xFF3B2948, 0xFF523F5F, 0xFFF2DAFF}, new int[] {0xFF111318, 0xFFE2E2E9, 0xFF43474E, 0xFFC3C6CF, 0xFF8D9199, 0xFF43474E, 0xFFE2E2E9, 0xFF2F3035, 0xFF111318, 0xFF37393F, 0xFF0C0E13, 0xFF191C20, 0xFF1D2024, 0xFF282A2F, 0xFF33353A}, true)
                : new ThemeColors(new int[] {0xFF0061A4, 0xFFFFFFFF, 0xFFD1E4FF, 0xFF001D36}, new int[] {0xFF535F70, 0xFFFFFFFF, 0xFFD7E3F7, 0xFF101C2B}, new int[] {0xFF6B5778, 0xFFFFFFFF, 0xFFF2DAFF, 0xFF251431}, new int[] {0xFFF8F9FF, 0xFF191C20, 0xFFDFE2EB, 0xFF43474E, 0xFF73777F, 0xFFC3C6CF, 0xFF2E3035, 0xFFEFF0F7, 0xFFD9DAE1, 0xFFF8F9FF, 0xFFFFFFFF, 0xFFF2F3FA, 0xFFECEEF4, 0xFFE6E8EE, 0xFFE0E2E9}, false);
    }

    static ThemeColors forest(boolean dark) {
        return dark ? new ThemeColors(new int[] {0xFF94D59D, 0xFF003912, 0xFF175225, 0xFFB2F2B8}, new int[] {0xFFB9CCB4, 0xFF253423, 0xFF3B4B38, 0xFFD5E8CF}, new int[] {0xFFA0CFD4, 0xFF00363B, 0xFF1E4D52, 0xFFBCEBF0}, new int[] {0xFF10150F, 0xFFE0E4DB, 0xFF424940, 0xFFC2C9BD, 0xFF8C9387, 0xFF424940, 0xFFE0E4DB, 0xFF2D322B, 0xFF10150F, 0xFF363B33, 0xFF0B100A, 0xFF191E17, 0xFF1D221B, 0xFF272C25, 0xFF32372F}, true)
                : new ThemeColors(new int[] {0xFF316B3A, 0xFFFFFFFF, 0xFFB2F2B8, 0xFF002108}, new int[] {0xFF526350, 0xFFFFFFFF, 0xFFD5E8CF, 0xFF101F0F}, new int[] {0xFF38656A, 0xFFFFFFFF, 0xFFBCEBF0, 0xFF002023}, new int[] {0xFFF7FBF2, 0xFF181D17, 0xFFDEE5D8, 0xFF424940, 0xFF73796E, 0xFFC2C9BD, 0xFF2D322B, 0xFFEFF2EA, 0xFFD7DCD3, 0xFFF7FBF2, 0xFFFFFFFF, 0xFFF1F5ED, 0xFFEBEFE7, 0xFFE5E9E1, 0xFFDFE4DC}, false);
    }

    static ThemeColors amber(boolean dark) {
        return dark ? new ThemeColors(new int[] {0xFFFFB95B, 0xFF472A00, 0xFF643F00, 0xFFFFDDB0}, new int[] {0xFFDEC3A2, 0xFF3F2D16, 0xFF57432B, 0xFFFBDCBA}, new int[] {0xFFB9CD9F, 0xFF253514, 0xFF3B4C29, 0xFFD5E9BA}, new int[] {0xFF19130C, 0xFFEEDFCF, 0xFF504538, 0xFFD3C4B4, 0xFF9D8E80, 0xFF504538, 0xFFEEDFCF, 0xFF372F26, 0xFF19130C, 0xFF403830, 0xFF130D07, 0xFF221B14, 0xFF261F18, 0xFF312A22, 0xFF3C352D}, true)
                : new ThemeColors(new int[] {0xFF855300, 0xFFFFFFFF, 0xFFFFDDB0, 0xFF2A1700}, new int[] {0xFF705B40, 0xFFFFFFFF, 0xFFFBDCBA, 0xFF281904}, new int[] {0xFF52643E, 0xFFFFFFFF, 0xFFD5E9BA, 0xFF102004}, new int[] {0xFFFFF8F3, 0xFF211A13, 0xFFEFE0CF, 0xFF504538, 0xFF837568, 0xFFD3C4B4, 0xFF372F26, 0xFFFCEFE2, 0xFFE5D8CB, 0xFFFFF8F3, 0xFFFFFFFF, 0xFFFFF1E5, 0xFFF9EBDD, 0xFFF3E5D7, 0xFFEDDFD1}, false);
    }
}
