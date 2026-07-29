package net.calca.biomesofcataclysms.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Objects;

import static net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint.clientData;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    @Nullable
    private ClientLevel level;

    // ==========================================
    // 1. MODIFICA FASE LUNARE 1 (Vanilla)
    // ==========================================
    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMoonPhase()I"
            )
    )
    private int boc$redirectMoonPhase(ClientLevel instance) {
        //Se non è ETERNAL ECLIPSE, non si attiva
        if (clientData.skyData.isInDeletedBiome && ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE){
            return Objects.requireNonNullElseGet(clientData.skyData.lunarPhase, instance::getMoonPhase);
        }else{
            return instance.getMoonPhase();
        }
    }

    // ==========================================
    // 2. MODIFICA SCALA LUNA 1 (Vanilla)
    // ==========================================
    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 20.0F)
    )
    private float boc$modifyMoonSize(float originalSize) {
        if (ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE){
            return originalSize * clientData.skyData.scale;
        }else{
            return originalSize;
        }
    }

    // ==========================================
    // 3. MODIFICA COLORE LUNA 1 (Vanilla)
    // ==========================================
    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
                    ordinal = 1 // Ordinale 1 punta alla Luna
            )
    )
    private void boc$applyMoonColor(CallbackInfo ci) {
        RenderSystem.setShaderColor(
                clientData.skyData.red,
                clientData.skyData.green,
                clientData.skyData.blue,
                clientData.skyData.alpha
        );
    }

    // ==========================================
    // 4. RENDERING SECONDA LUNA E RESET COLORE (A coda di renderSky)
    // ==========================================
    @Inject(
            method = "renderSky",
            at = @At("TAIL")
    )
    private void boc$renderSecondMoonAndReset(
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci
    ) {
        // Se la seconda luna è abilitata e il livello client esiste, procediamo al disegno 3D
        if (clientData.skyData.isSecondMoonActive
                && this.level != null
                && ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE) {
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(modelViewMatrix);

            // --- ALLINEAMENTO SU ASSE EST-OVEST (WEST) ---
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));

            // --- CALCOLO DELLA ROTAZIONE REALE DEL SERVER (CON CURVA NATIVA) ---
            float skyAngle = 0.0F;

            if (this.level.getLevelData() instanceof ClientLevelDataAccessor dataAccessor) {
                // 1. Prendiamo il tempo reale del server (salvato dall'accessor)
                long realServerDayTime = clientData.skyData.serverDayTime;

                // 2. CORREZIONE MATEMATICA: Invece di fare la divisione lineare,
                // passiamo il tempo nella funzione di warping nativa di Minecraft.
                // Questo applicherà la stessa identica curva di accelerazione/decelerazione della luna vanilla!
                skyAngle = this.level.dimensionType().timeOfDay(realServerDayTime);
            } else {
                // Fallback di sicurezza
                skyAngle = this.level.getTimeOfDay(partialTick);
            }

            // Angolo finale ora perfettamente identico alla luna vanilla (se moltiplicatore = 1.0F e offset = 0)
            float customAngle = (skyAngle * clientData.skyData.secondMoonSpeedMultiplier * 360.0F)
                    + 180.0F
                    + clientData.skyData.secondMoonAngleOffset;

            poseStack.mulPose(Axis.XP.rotationDegrees(customAngle));
            poseStack.mulPose(Axis.ZP.rotationDegrees(clientData.skyData.secondMoonOrbitInclination));

            Matrix4f matrix = poseStack.last().pose();

            // --- CONFIGURAZIONE BLENDING E TRASPARENZA (Risolve quadrato nero) ---
            RenderSystem.enableBlend();
            // Questa è la funzione di blending specifica per applicare l'alpha della texture sullo sfondo del cielo
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );

            // Proietta la seconda luna all'infinito impedendole di tagliare i blocchi
            RenderSystem.disableDepthTest();

            // Utilizziamo lo shader nativo per le texture posizionate (senza sovrascrittura di colori che rompono l'alpha)
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, clientData.skyData.secondMoonTexture);

            // Applichiamo il colore con l'opacità (Alpha) corretta della seconda luna
            RenderSystem.setShaderColor(
                    clientData.skyData.secondRed,
                    clientData.skyData.secondGreen,
                    clientData.skyData.secondBlue,
                    clientData.skyData.secondAlpha
            );

            // --- CALCOLO DELLE COORDINATE UV DELLA FASE LUNARE ---
            int phase = clientData.skyData.secondLunarPhase != null ?
                    clientData.skyData.secondLunarPhase :
                    this.level.getMoonPhase();

            int xOffset = phase % 4;
            int yOffset = phase / 4 % 2;
            float u1 = (float) xOffset / 4.0F;
            float v1 = (float) yOffset / 2.0F;
            float u2 = (float) (xOffset + 1) / 4.0F;
            float v2 = (float) (yOffset + 1) / 2.0F;

            // --- DISEGNO DELLA SECONDA LUNA (Quad 3D) ---
            float size = 20.0F * clientData.skyData.secondScale;
            float distanceY = -100.0F; // Distanza standard della volta celeste

            Tesselator tesselator = Tesselator.getInstance();
            // Usiamo il formato POSITION_TEX (senza COLOR interno nei vertici) per far gestire l'alpha al 100% allo shader della texture
            BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            bufferBuilder.addVertex(matrix, -size, distanceY, size).setUv(u2, v2);
            bufferBuilder.addVertex(matrix, size, distanceY, size).setUv(u1, v2);
            bufferBuilder.addVertex(matrix, size, distanceY, -size).setUv(u1, v1);
            bufferBuilder.addVertex(matrix, -size, distanceY, -size).setUv(u2, v1);

            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

            // --- RIPRISTINO STATO GRAFICO ---
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        // Resettiamo il colore globale a bianco per non buggare i render successivi
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
 }