package com.nova.license;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Enumeration;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Vérifie une clé de licence Nova Client hors-ligne, sans serveur.
 *
 * Format d'une clé : "NOVA-xxxx-xxxx-xxxx.<signature base64url>"
 * La signature est faite avec Ed25519 par keygen.js (gardé privé côté vendeur).
 * Ici on ne vérifie qu'avec la clé PUBLIQUE : impossible de forger une clé
 * valide même en lisant tout le code de cette classe ou le .jar décompilé.
 */
public class LicenseManager {

    // Colle ici le contenu de nova-license-pubkey.txt (sortie de `node keygen.js setup`)
    private static final String PUBLIC_KEY_B64 = "COLLE_TA_CLE_PUBLIQUE_ICI";

    private static final Path LICENSE_FILE =
        Paths.get(System.getProperty("user.home"), ".minecraft", "config", "nova", "license.key");

    private static boolean verified = false;

    /** À appeler au démarrage du mod (dans NovaClient#onInitializeClient). */
    public static boolean isLicensed() {
        if (verified) return true;
        try {
            if (!Files.exists(LICENSE_FILE)) return false;
            String key = Files.readString(LICENSE_FILE).trim();
            verified = verify(key);
            return verified;
        } catch (Exception e) {
            return false;
        }
    }

    /** Appelée depuis l'écran qui demande la clé au premier lancement. */
    public static boolean activate(String rawKey) {
        rawKey = rawKey.trim();
        if (!verify(rawKey)) return false;
        try {
            Files.createDirectories(LICENSE_FILE.getParent());
            Files.writeString(LICENSE_FILE, rawKey);
            verified = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean verify(String fullKey) {
        try {
            int dot = fullKey.indexOf('.');
            if (dot < 0) return false;
            String id = fullKey.substring(0, dot);
            byte[] sig = Base64.getUrlDecoder().decode(fullKey.substring(dot + 1));

            byte[] pubBytes = Base64.getDecoder().decode(PUBLIC_KEY_B64);
            PublicKey pub = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(pubBytes));

            // la clé a été signée pour id + ID machine : si copiée sur un autre PC,
            // l'ID machine calculé ici est différent et la signature ne colle plus.
            String payload = id + "|" + getHardwareId();

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pub);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Identifiant de cette machine, dérivé de l'adresse MAC de la première
     * interface réseau physique trouvée. À afficher à l'acheteur pour qu'il
     * le transmette au vendeur (il sert à générer une clé liée à son PC).
     */
    public static String getHardwareId() {
        try {
            StringBuilder raw = new StringBuilder(System.getProperty("os.name", ""));
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface ni = nets.nextElement();
                if (ni.isLoopback() || ni.isVirtual()) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) continue;
                for (byte b : mac) raw.append(String.format("%02X", b));
                break;
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02X", hash[i]));
            return hex.toString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}

/**
 * Écran affiché au lancement si aucune clé valide n'est enregistrée.
 * Bloque l'accès au mod tant que LicenseManager.activate(...) ne renvoie pas true.
 * (Classe non publique : c'est pour ça qu'elle peut vivre dans le même fichier
 * que LicenseManager, qui elle est publique et donne son nom au fichier.)
 */
class LicenseActivationScreen extends Screen {

    private TextFieldWidget input;
    private Text status = Text.literal("");

    public LicenseActivationScreen() {
        super(Text.literal("Nova Client — Activation"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.input = new TextFieldWidget(this.textRenderer, cx - 110, cy - 10, 220, 20, Text.literal("Clé de licence"));
        this.input.setMaxLength(128);
        this.input.setPlaceholder(Text.literal("NOVA-xxxx-xxxx-xxxx.signature"));
        this.addSelectableChild(this.input);
        this.setInitialFocus(this.input);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Activer"), btn -> tryActivate())
            .dimensions(cx - 50, cy + 18, 100, 20)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Copier mon ID machine"), btn ->
                MinecraftClient.getInstance().keyboard.setClipboard(LicenseManager.getHardwareId()))
            .dimensions(cx - 75, cy + 44, 150, 20)
            .build());
    }

    private void tryActivate() {
        String key = this.input.getText();
        if (LicenseManager.activate(key)) {
            this.status = Text.literal("Clé valide — bienvenue.");
            MinecraftClient.getInstance().execute(() -> this.close());
        } else {
            this.status = Text.literal("Clé invalide. Vérifie que tu l'as copiée en entier.");
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Entre ta clé Nova Client", cx, this.height / 2 - 35, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            "Pas encore de clé ? Ton ID machine : " + LicenseManager.getHardwareId(),
            cx, this.height / 2 + 70, 0x888888);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.status, cx, this.height / 2 + 45, 0xAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // reste bloquant tant que pas activé : empêche de fermer avec Échap
        return false;
    }
}
