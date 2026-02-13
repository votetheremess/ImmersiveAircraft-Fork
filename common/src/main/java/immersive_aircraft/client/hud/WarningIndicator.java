package immersive_aircraft.client.hud;

import immersive_aircraft.client.OverlayRenderer;
import immersive_aircraft.entity.EngineVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.NoteBlock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static immersive_aircraft.client.hud.Colors.*;

public class WarningIndicator implements Indicator {
    public static final WarningIndicator INSTANCE = new WarningIndicator();
    // Flash "speed" values are full cycles (on+off) in ticks.
    private static final int STABILIZER_FLASH_CYCLE_TICKS = 10;
    private static final int STABILIZER_OFF_FLASH_CYCLES = 2;
    private static final int STABILIZER_ON_FLASH_CYCLES = 1;
    private static final int WARNING_FLASH_CYCLE_TICKS = 3;
    private static final int DAMAGED_FLASH_CYCLE_TICKS = 1;
    private static final int PULL_UP_FLASH_CYCLE_TICKS = 3;

    private boolean miniHUD = false;
    private boolean cWarning = false;
    private boolean cMsl = false;
    private boolean warningBeepHighPitch = true;

    public EnumMap<EngineVehicle.Cautions, Boolean> cMap = new EnumMap<>(EngineVehicle.Cautions.class);
    private final EnumMap<EngineVehicle.Cautions, Integer> cautionFlashStartTick = new EnumMap<>(EngineVehicle.Cautions.class);
    private final EnumMap<EngineVehicle.Cautions, Boolean> cautionWasActive = new EnumMap<>(EngineVehicle.Cautions.class);

    public WarningIndicator() {
        for (EngineVehicle.Cautions c : EngineVehicle.Cautions.values()) {
            cMap.compute(c, (cautions, v) -> false);
            cautionFlashStartTick.put(c, 0);
            cautionWasActive.put(c, false);
        }
    }

    @Override
    public void update(Minecraft client, EngineVehicle aircraft) {
        if (!aircraft.level().isClientSide() || client.isPaused()) {
            return;
        }

        if (aircraft.mslWarning > 0) {
            if (OverlayRenderer.INSTANCE.tick % 10 == 0) {
                cMsl = !cMsl;
            }
            if (OverlayRenderer.INSTANCE.tick % 5 == 0) {
                aircraft.level().playLocalSound(aircraft.getX(), aircraft.getY() + aircraft.getBbHeight() * 0.5, aircraft.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(),
                        aircraft.getSoundSource(), 1.0f, NoteBlock.getPitchFromNote(24), false);
                aircraft.level().playLocalSound(aircraft.getX(), aircraft.getY() + aircraft.getBbHeight() * 0.5, aircraft.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(),
                        aircraft.getSoundSource(), 1.0f, NoteBlock.getPitchFromNote(22), false);
            }
        } else {
            cMsl = false;
        }

        if (aircraft.mainWarning > 0) {
            int warningPhase = aircraft.tickCount % WARNING_FLASH_CYCLE_TICKS;
            cWarning = isOnPhase(warningPhase, WARNING_FLASH_CYCLE_TICKS);
            if (warningPhase == 0) {
                float warningPitch = NoteBlock.getPitchFromNote(warningBeepHighPitch ? 16 : 24);
                warningBeepHighPitch = !warningBeepHighPitch;
                if (!cMsl) {
                    aircraft.level().playLocalSound(aircraft.getX(), aircraft.getY() + aircraft.getBbHeight() * 0.5, aircraft.getZ(),
                            SoundEvents.NOTE_BLOCK_BIT.value(),
                            aircraft.getSoundSource(), 1.0f, warningPitch, false);
                }
            }
        } else {
            cWarning = false;
            warningBeepHighPitch = true;
        }

        for (EngineVehicle.Cautions c : EngineVehicle.Cautions.values()) {
            if (aircraft.cautions.get(c) > 0) {
                if (isStabilizerCaution(c)) {
                    if (!Boolean.TRUE.equals(cautionWasActive.get(c))) {
                        cautionWasActive.put(c, true);
                        cautionFlashStartTick.put(c, aircraft.tickCount);
                    }
                    int elapsed = Math.max(0, aircraft.tickCount - cautionFlashStartTick.get(c));
                    cMap.put(c, shouldRenderStabilizerFlash(c, elapsed));
                    continue;
                }

                if (c == EngineVehicle.Cautions.PULL_UP) {
                    int pullUpPhase = aircraft.tickCount % PULL_UP_FLASH_CYCLE_TICKS;
                    cMap.put(c, isOnPhase(pullUpPhase, PULL_UP_FLASH_CYCLE_TICKS));
                    if (aircraft.mainWarning == 0
                            && aircraft.mslWarning == 0
                            && c.defaultBeepEnabled()
                            && pullUpPhase == 0) {
                        aircraft.level().playLocalSound(aircraft.getX(), aircraft.getY() + aircraft.getBbHeight() * 0.5, aircraft.getZ(),
                                SoundEvents.NOTE_BLOCK_BIT.value(),
                                aircraft.getSoundSource(), 1.0f, NoteBlock.getPitchFromNote(5), false);
                    }
                    continue;
                }

                if (c == EngineVehicle.Cautions.DAMAGED) {
                    int damagedPhase = aircraft.tickCount % DAMAGED_FLASH_CYCLE_TICKS;
                    cMap.put(c, isOnPhase(damagedPhase, DAMAGED_FLASH_CYCLE_TICKS));
                    if (aircraft.mainWarning == 0 && aircraft.mslWarning == 0 && c.defaultBeepEnabled()) {
                        if (damagedPhase == 0) {
                            aircraft.level().playLocalSound(aircraft.getX(), aircraft.getY() + aircraft.getBbHeight() * 0.5, aircraft.getZ(),
                                    SoundEvents.NOTE_BLOCK_BIT.value(),
                                    aircraft.getSoundSource(), 1.0f, NoteBlock.getPitchFromNote(16), false);
                        } else if (damagedPhase == getSecondBeepPhase(DAMAGED_FLASH_CYCLE_TICKS)) {
                            aircraft.level().playLocalSound(aircraft.getX(), aircraft.getY() + aircraft.getBbHeight() * 0.5, aircraft.getZ(),
                                    SoundEvents.NOTE_BLOCK_BIT.value(),
                                    aircraft.getSoundSource(), 1.0f, NoteBlock.getPitchFromNote(24), false);
                        }
                    }
                    continue;
                }

                if (OverlayRenderer.INSTANCE.tick % 15 == 0) {
                    cMap.compute(c, (cautions, v) -> Boolean.FALSE.equals(v));
                }
                if (aircraft.mainWarning == 0
                        && aircraft.mslWarning == 0
                        && c.defaultBeepEnabled()
                        && OverlayRenderer.INSTANCE.tick % 60 == 0) {
                    aircraft.level().playLocalSound(aircraft.getX(), aircraft.getY() + aircraft.getBbHeight() * 0.5, aircraft.getZ(),
                            SoundEvents.NOTE_BLOCK_BIT.value(),
                            aircraft.getSoundSource(), 1.0f, NoteBlock.getPitchFromNote(5), false);
                }
            } else {
                cautionWasActive.put(c, false);
                cautionFlashStartTick.put(c, 0);
                cMap.put(c, false);
            }
        }
    }

    private boolean isStabilizerCaution(EngineVehicle.Cautions caution) {
        return caution == EngineVehicle.Cautions.STABILIZER_ON || caution == EngineVehicle.Cautions.STABILIZER_OFF;
    }

    private int getStabilizerFlashCycles(EngineVehicle.Cautions caution) {
        return caution == EngineVehicle.Cautions.STABILIZER_OFF ? STABILIZER_OFF_FLASH_CYCLES : STABILIZER_ON_FLASH_CYCLES;
    }

    private boolean isOnPhase(int phaseTick, int cycleTicks) {
        int onTicks = (cycleTicks + 1) / 2;
        return phaseTick < onTicks;
    }

    private int getSecondBeepPhase(int cycleTicks) {
        return Math.max(1, cycleTicks / 2);
    }

    private boolean shouldRenderStabilizerFlash(EngineVehicle.Cautions caution, int elapsedTicks) {
        int cycleTicks = STABILIZER_FLASH_CYCLE_TICKS;
        int totalFlashTicks = cycleTicks * getStabilizerFlashCycles(caution);
        if (elapsedTicks >= totalFlashTicks) {
            return false;
        }
        return isOnPhase(elapsedTicks % cycleTicks, cycleTicks);
    }

    public void drawDashboard(GuiGraphics context, Minecraft client, int baseX, int baseY, EngineVehicle aircraft, int color) {
        miniHUD = true;
        drawHUD(context, client, baseX, baseY - 18, 100, aircraft, color, null);
        miniHUD = false;
    }

    public void drawHUD(GuiGraphics context, Minecraft client, int baseX, int baseY, int width, EngineVehicle aircraft, int color, int[] edge) {
        int lineSpacing = client.font.lineHeight + 2;
        int linesDrawn = 0;
        List<String> cautionLines = new ArrayList<>();

        addCaution(cautionLines, EngineVehicle.Cautions.PULL_UP);
        addCaution(cautionLines, EngineVehicle.Cautions.STABILIZER_OFF);
        addCaution(cautionLines, EngineVehicle.Cautions.STABILIZER_ON);
        for (EngineVehicle.Cautions caution : EngineVehicle.Cautions.values()) {
            if (caution == EngineVehicle.Cautions.PULL_UP
                    || caution == EngineVehicle.Cautions.STABILIZER_OFF
                    || caution == EngineVehicle.Cautions.STABILIZER_ON) {
                continue;
            }
            addCaution(cautionLines, caution);
        }

        for (String caution : cautionLines) {
            int y = baseY - linesDrawn * lineSpacing;
            if (edgeCheck(edge, client.font.width(caution) / 4, client.font.lineHeight / 2, baseX + 1, y)) {
                StringDrawer.drawString2(context, client, caution, baseX + 1, y, color, miniHUD);
            }
            linesDrawn++;
        }

        if (cMsl || cWarning) {
            String warning = cMsl ? "[MISSILE]" : "[WARNING]";
            int y = baseY - linesDrawn * lineSpacing;
            if (edgeCheck(edge, client.font.width(warning) / 4, client.font.lineHeight / 2, baseX + 1, y)) {
                StringDrawer.drawString8(context, client, warning, baseX + 1, y, color, miniHUD);
            }
        }
    }

    private void addCaution(List<String> cautionLines, EngineVehicle.Cautions caution) {
        if (Boolean.TRUE.equals(cMap.get(caution))) {
            cautionLines.add("[" + caution.name().toUpperCase().replace('_', ' ') + "]");
        }
    }

    @Override
    public void drawDials(GuiGraphics context, Minecraft client, int baseX, int baseY, int scale, EngineVehicle aircraft) {
        // dial 29x75
        context.fill(baseX - 14, baseY - 37, baseX + 14 + 1, baseY + 37 + 1, colorBG);

        // border
        context.fill(baseX - 14, baseY - 37, baseX + 14 + 1, baseY - 35, colorFG);
        context.fill(baseX - 14, baseY - 37, baseX - 12, baseY + 37 + 1, colorFG);
        context.fill(baseX - 14, baseY + 35 + 1, baseX + 14 + 1, baseY + 37 + 1, colorFG);
        context.fill(baseX + 12 + 1, baseY - 37, baseX + 14 + 1, baseY + 37 + 1, colorFG);
        OverlayRenderer.drawScrew(context, baseX, baseY - 32, 1, true, colorFG);
        OverlayRenderer.drawScrew(context, baseX, baseY + 32, 1, false, colorFG);

        // caution lamp
        context.fill(baseX - 11, baseY - 26, baseX + 11 + 1, baseY - 6 + 1, colorFG);
        context.fill(baseX - 10, baseY - 25, baseX + 10 + 1, baseY - 7 + 1, cWarning ? colorLt1 : colorLt0);
        StringDrawer.drawString5(context, client, "MAIN", baseX + 2, baseY - 16, colorLt3, false);
        context.fill(baseX - 11, baseY - 2, baseX + 11 + 1, baseY + 10 + 1, colorFG);
        context.fill(baseX - 10, baseY - 1, baseX + 10 + 1, baseY + 9 + 1, cMap.get(EngineVehicle.Cautions.VOID) ? colorLt2 : colorLt0);
        StringDrawer.drawString5(context, client, "VOID", baseX + 2, baseY + 4, colorLt3, false);
        context.fill(baseX - 11, baseY + 14, baseX + 11 + 1, baseY + 26 + 1, colorFG);
        context.fill(baseX - 10, baseY + 15, baseX + 10 + 1, baseY + 25 + 1, cMap.get(EngineVehicle.Cautions.DAMAGED) ? colorLt2 : colorLt0);
        StringDrawer.drawString5(context, client, "DMG", baseX + 2, baseY + 20, colorLt3, false);
    }
}
