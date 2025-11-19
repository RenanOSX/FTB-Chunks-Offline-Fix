package dev.ftb.mods.ftbteams.data;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.util.NetworkHelper;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import dev.ftb.mods.ftbteams.net.UpdatePresenceMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerTeam extends AbstractTeam {
	private String playerName;
	private boolean online;
	private AbstractTeam effectiveTeam;

	public PlayerTeam(TeamManagerImpl manager, UUID id) {
		super(manager, id);

		playerName = "";
		online = false;
		effectiveTeam = this;
	}

	@Override
	public UUID getTeamId() {
		return effectiveTeam.getId();
	}

	@Override
	public TeamType getType() {
		return TeamType.PLAYER;
	}

	@Override
	public boolean isPlayerTeam() {
		return true;
	}

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public boolean isOnline() {
		return online;
	}

	public void setOnline(boolean online) {
		this.online = online;
	}

	public AbstractTeam getEffectiveTeam() {
		return effectiveTeam;
	}

	public void setEffectiveTeam(AbstractTeam effectiveTeam) {
		this.effectiveTeam = effectiveTeam;
	}

	@Override
	protected void serializeExtraNBT(CompoundTag tag) {
		tag.putString("player_name", playerName);
	}

	@Override
	public void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider) {
		super.deserializeNBT(tag, provider);
		playerName = tag.getString("player_name");
	}

	@Nullable
	public ServerPlayer getPlayer() {
		ServerPlayer player = FTBTUtils.getPlayerByUUID(manager.getServer(), id);
		if (player == null && !manager.getServer().usesAuthentication()) {
			// Offline servers may assign different UUIDs when auth is disabled; fall back to a name match
			player = manager.getServer().getPlayerList().getPlayerByName(playerName);
		}
		return player;
	}

	@Override
	public TeamRank getRankForPlayer(UUID playerId) {
		return playerId.equals(id) ? TeamRank.OWNER : super.getRankForPlayer(playerId);
	}

	@Override
	public List<ServerPlayer> getOnlineMembers() {
		ServerPlayer p = getPlayer();
		return p == null ? Collections.emptyList() : Collections.singletonList(p);
	}

	public void updatePresence() {
		NetworkHelper.sendToAll(manager.getServer(), new UpdatePresenceMessage(createClientPlayer()));
	}

	public Team createParty(UUID playerId, @Nullable ServerPlayer player, String name, String description, int color, Set<GameProfile> invited) {
		try {
			PartyTeam team = manager.createParty(playerId, player, name, description, Color4I.rgb(color));
			if (player != null) {
				team.invite(player, invited);
			}
			return team;
		} catch (CommandSyntaxException ex) {
			if (player != null) {
				player.displayClientMessage(Component.literal(ex.getMessage()).withStyle(ChatFormatting.RED), false);
			}
			return null;
		}
	}

	public boolean hasTeam() {
		return effectiveTeam != this;
	}

	public KnownClientPlayer createClientPlayer() {
		GameProfile profile = new GameProfile(getId(), getPlayerName());
		ServerPlayer onlinePlayer = getPlayer();
		if (onlinePlayer != null) {
			profile = FTBTUtils.normalize(onlinePlayer.getGameProfile());
		} else {
			MinecraftServer server = manager.getServer();
			if (!server.usesAuthentication()) {
				var profileCache = server.getProfileCache();
				if (profileCache != null) {
					profile = profileCache.get(getPlayerName())
							.map(FTBTUtils::normalize)
							.orElse(profile);
				}
			}
		}

		return new KnownClientPlayer(
				getId(),
				getPlayerName(),
				isOnline(),
				getTeamId(),
				profile,
				getExtraData()
		);
	}
}