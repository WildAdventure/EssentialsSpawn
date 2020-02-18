package com.earth2me.essentials.spawn;

import static com.earth2me.essentials.I18n.*;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import com.earth2me.essentials.Kit;
import com.earth2me.essentials.OfflinePlayer;
import com.earth2me.essentials.User;
import com.earth2me.essentials.textreader.IText;
import com.earth2me.essentials.textreader.KeywordReplacer;
import com.earth2me.essentials.textreader.SimpleTextPager;

import net.ess3.api.IEssentials;

public class EssentialsSpawnPlayerListener implements Listener {
	
	private static final Logger LOGGER = Bukkit.getLogger();
	private final transient IEssentials ess;
	private final transient SpawnStorage spawns;
	private final transient Set<UUID> newPlayers;

	public EssentialsSpawnPlayerListener(final IEssentials ess, final SpawnStorage spawns) {
		super();
		this.ess = ess;
		this.spawns = spawns;
		this.newPlayers = new HashSet<>();
	}

	public void onPlayerRespawn(final PlayerRespawnEvent event) {
		final User user = ess.getUser(event.getPlayer());

		if (user.isJailed() && user.getJail() != null && !user.getJail().isEmpty()) {
			return;
		}

		if (ess.getSettings().getRespawnAtHome()) {
			Location home;
			final Location bed = user.getBase().getBedSpawnLocation();
			if (bed != null) {
				home = bed;
			} else {
				home = user.getHome(user.getLocation());
			}
			if (home != null) {
				event.setRespawnLocation(home);
				return;
			}
		}
		final Location spawn = spawns.getSpawn(user.getGroup());
		if (spawn != null) {
			event.setRespawnLocation(spawn);
		}
	}

	public void onPlayerJoinLowest(final PlayerJoinEvent event) {
		if (!ess.getUserMap().userExists(event.getPlayer().getUniqueId())) { // Se l'utente non esiste nei file, consideriamolo nuovo
			newPlayers.add(event.getPlayer().getUniqueId());
		}
	}

	public void onPlayerJoin(final PlayerJoinEvent event) {
		if (newPlayers.remove(event.getPlayer().getUniqueId())) { // Vuol dire che era nei newPlayers se remove ritorna true
			ess.runTaskAsynchronously(() -> {
				delayedJoin(event.getPlayer());
			});
		}
	}

	public void delayedJoin(Player player) {
		final User user = ess.getUser(player);

		if (!"none".equalsIgnoreCase(ess.getSettings().getNewbieSpawn())) {
			ess.scheduleSyncDelayedTask(new NewPlayerTeleport(user), 1L);
		}

		ess.scheduleSyncDelayedTask(new Runnable() {
			@Override
			public void run() {
				if (!user.getBase().isOnline()) {
					return;
				}

				//This method allows for multiple line player announce messages using multiline yaml syntax #EasterEgg
				if (ess.getSettings().getAnnounceNewPlayers()) {
					final IText output = new KeywordReplacer(ess.getSettings().getAnnounceNewPlayerFormat(), user.getSource(), ess);
					final SimpleTextPager pager = new SimpleTextPager(output);

					for (String line : pager.getLines()) {
						ess.broadcastMessage(user, line);
					}
				}

				final String kitName = ess.getSettings().getNewPlayerKit();
				if (!kitName.isEmpty()) {
					try {
						final Map<String, Object> kit = ess.getSettings().getKit(kitName.toLowerCase(Locale.ENGLISH));
						final List<String> items = Kit.getItems(ess, user, kitName, kit);
						Kit.expandItems(ess, user, items);
					} catch (Exception ex) {
						LOGGER.log(Level.WARNING, ex.getMessage());
					}
				}

				LOGGER.log(Level.FINE, "New player join");
			}
		});
	}

	private class NewPlayerTeleport implements Runnable {
		private final transient User user;

		public NewPlayerTeleport(final User user) {
			this.user = user;
		}

		@Override
		public void run() {
			if (user.getBase() instanceof OfflinePlayer) {
				return;
			}

			try {
				final Location spawn = spawns.getSpawn(ess.getSettings().getNewbieSpawn());
				if (spawn != null) {
					user.getTeleport().now(spawn, false, TeleportCause.PLUGIN);
				}
			} catch (Exception ex) {
				Bukkit.getLogger().log(Level.WARNING, tl("teleportNewPlayerError"), ex);
			}
		}
	}
}
