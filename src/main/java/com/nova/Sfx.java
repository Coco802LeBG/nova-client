package com.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** All Nova sounds (vanilla sound events, so no resource pack is needed). */
public final class Sfx {
	private static int testIdx = 0;

	private Sfx() {}

	private static float vol(float v) {
		return (float) (v * Session.I.cfg.soundVolume);
	}

	private static boolean on() {
		return Session.I.cfg.sounds && Minecraft.getInstance().player != null;
	}

	private static void play(SoundEvent e, float pitch, float v) {
		if (!on()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol(v)));
	}

	private static void play(Holder<SoundEvent> e, float pitch, float v) {
		if (!on()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e.value(), pitch, vol(v)));
	}

	public static void bid() { play(SoundEvents.NOTE_BLOCK_PLING, 1.3f, 0.9f); }

	public static void start() { play(SoundEvents.NOTE_BLOCK_CHIME, 1.2f, 0.8f); }

	public static void sold() {
		play(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.7f);
		play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f, 0.8f);
	}

	public static void noBids() { play(SoundEvents.NOTE_BLOCK_BASS, 0.7f, 0.9f); }

	public static void win() { play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f, 0.9f); }

	public static void loss() { play(SoundEvents.NOTE_BLOCK_BASS, 0.6f, 1.0f); }

	/** Countdown tick, higher pitch as the timer gets close to 0. */
	public static void tick(int sec) {
		if (!Session.I.cfg.soundTick) return;
		play(SoundEvents.NOTE_BLOCK_HAT, 0.9f + (5 - Math.max(1, Math.min(5, sec))) * 0.15f, 0.8f);
	}

	public static void menu() {
		if (Session.I.cfg.soundMenu) play(SoundEvents.NOTE_BLOCK_CHIME, 1.6f, 0.4f);
	}

	/** Small UI feedback for buttons. */
	public static void click() {
		if (Session.I.cfg.soundClick) play(SoundEvents.UI_BUTTON_CLICK, 1.0f, 0.45f);
	}

	/** Plays the next sound in the list each time you press "Test sounds". */
	public static void test() {
		switch (testIdx++ % 8) {
			case 0 -> start();
			case 1 -> bid();
			case 2 -> tick(1);
			case 3 -> win();
			case 4 -> loss();
			case 5 -> sold();
			case 6 -> noBids();
			default -> click();
		}
	}
}
