package com.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class NovaScreen extends Screen {
	private static final int W = 420, H = 240;
	private static final long[] MINS = { 1, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000 };
	private static final int[] SECS = { 10, 15, 30, 60 };
	private static final long[] WORTHS = { 0, 1_000_000, 10_000_000, 100_000_000, 500_000_000, 1_000_000_000 };
	private static final String[] TABS = { "Auction", "Recent", "Gamble", "Settings" };

	private static int tab = 0, minI = 3, secI = 1, worthI = 0;
	private int x0, y0, cx;

	public NovaScreen() {
		super(Component.literal("Nova"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static Component c(String s) {
		return Component.literal(s);
	}

	private void add(String label, int x, int y, int w, Runnable r) {
		addRenderableWidget(Button.builder(c(label), b -> r.run()).bounds(x, y, w, 20).build());
	}

	@Override
	protected void init() {
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		cx = x0 + 104;
		Session s = Session.I;

		for (int i = 0; i < TABS.length; i++) {
			final int t = i;
			add((tab == i ? "> " : "") + TABS[i], x0 + 8, y0 + 34 + i * 24, 84, () -> { tab = t; rebuildWidgets(); });
		}

		if (tab == 0) {
			add("Min bid: " + Money.fmt(MINS[minI]), cx, y0 + 76, 170, () -> { minI = (minI + 1) % MINS.length; rebuildWidgets(); });
			add("Timer: " + SECS[secI] + "s", cx, y0 + 100, 170, () -> { secI = (secI + 1) % SECS.length; rebuildWidgets(); });
			add("Worth each: " + (WORTHS[worthI] == 0 ? "none" : Money.fmt(WORTHS[worthI])), cx, y0 + 124, 170, () -> { worthI = (worthI + 1) % WORTHS.length; rebuildWidgets(); });
			if (s.cur != null) {
				add("Cancel auction", cx, y0 + 156, 170, () -> { s.cancel(); onClose(); });
			} else {
				add("Start auction (held item)", cx, y0 + 156, 170, () -> {
					Minecraft mc = Minecraft.getInstance();
					ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
					if (held.isEmpty()) return;
					s.start(held, MINS[minI], WORTHS[worthI], SECS[secI]);
					onClose();
				});
			}
		} else if (tab == 1) {
			for (int i = 0; i < Math.min(5, s.history.size()); i++) {
				final int k = i;
				add("Redo", cx + 200, y0 + 40 + i * 28, 44, () -> { s.redo(s.history.get(k), SECS[secI]); s.history.remove(k); onClose(); });
				add("X", cx + 248, y0 + 40 + i * 28, 20, () -> { s.history.remove(k); rebuildWidgets(); });
			}
			add("Clear history", cx, y0 + H - 32, 110, () -> { s.history.clear(); rebuildWidgets(); });
		} else if (tab == 2) {
			add("Tracker: " + (s.gambleOn ? "ON" : "OFF"), cx, y0 + 40, 170, () -> { s.gambleOn = !s.gambleOn; rebuildWidgets(); });
			add("+ Win", cx, y0 + 132, 54, () -> s.manual(true));
			add("+ Loss", cx + 58, y0 + 132, 54, () -> s.manual(false));
			add("Undo", cx + 116, y0 + 132, 54, s::undo);
			add("Reset", cx, y0 + 156, 170, s::resetGamble);
		} else {
			add("Reload config", cx, y0 + 40, 170, s::reload);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (NovaClient.MENU.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		NovaHud.panel(g, x0, y0, W, H, NovaHud.BORDER);
		g.fill(x0 + 4, y0 + 4, x0 + 98, y0 + H - 4, 0x60000000);
		super.render(g, mx, my, pt);

		Session s = Session.I;
		g.drawString(font, "NOVA", x0 + 12, y0 + 12, NovaHud.WHITE);
		g.drawString(font, Minecraft.getInstance().getUser().getName(), x0 + 12, y0 + H - 14, NovaHud.MUTED);

		if (tab == 0) {
			ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
			g.drawString(font, "New auction", cx, y0 + 12, NovaHud.WHITE);
			if (held.isEmpty()) {
				g.drawString(font, "Hold the item you want to auction", cx, y0 + 50, NovaHud.MUTED);
			} else {
				g.renderItem(held, cx, y0 + 44);
				g.drawString(font, held.getHoverName().getString() + " x" + held.getCount(), cx + 22, y0 + 48, NovaHud.WHITE);
			}
			g.drawString(font, "Bids = chat lines \"X paid you $Y\"", cx, y0 + 184, NovaHud.MUTED);
		} else if (tab == 1) {
			g.drawString(font, "Recent auctions (" + s.history.size() + ")", cx, y0 + 12, NovaHud.WHITE);
			if (s.history.isEmpty()) g.drawString(font, "Nothing yet.", cx, y0 + 44, NovaHud.MUTED);
			for (int i = 0; i < Math.min(5, s.history.size()); i++) {
				Session.Record r = s.history.get(i);
				int y = y0 + 40 + i * 28;
				g.renderItem(r.stack, cx, y + 1);
				g.drawString(font, r.stack.getHoverName().getString() + " x" + r.qty, cx + 22, y, NovaHud.WHITE);
				g.drawString(font, r.top > 0 ? r.by + " won for $" + Money.fmt(r.top) : "No bids", cx + 22, y + 11, r.top > 0 ? NovaHud.GREEN : NovaHud.MUTED);
			}
		} else if (tab == 2) {
			g.drawString(font, "Gamble tracker", cx, y0 + 12, NovaHud.WHITE);
			g.drawString(font, "Wins: " + s.wins, cx, y0 + 70, NovaHud.GREEN);
			g.drawString(font, "Losses: " + s.losses, cx, y0 + 82, NovaHud.RED);
			g.drawString(font, "Win rate: " + s.winRate() + "%", cx, y0 + 94, NovaHud.WHITE);
			g.drawString(font, "Net: " + Money.signed(s.gambleNet), cx, y0 + 106, s.gambleNet >= 0 ? NovaHud.GREEN : NovaHud.RED);
			g.drawString(font, "Auto: paid you = win, you pay back 2x = loss", cx, y0 + 172, NovaHud.MUTED);
			g.drawString(font, "Numpad 1 = win, Numpad 2 = loss (menu closed)", cx, y0 + 184, NovaHud.MUTED);
		} else {
			g.drawString(font, "Settings", cx, y0 + 12, NovaHud.WHITE);
			g.drawString(font, "Menu key: Options > Controls > Nova Client", cx, y0 + 70, NovaHud.MUTED);
			g.drawString(font, "Chat patterns: config/nova.json", cx, y0 + 84, NovaHud.MUTED);
		}
	}
}
