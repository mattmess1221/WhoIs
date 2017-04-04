package mnm.whois;

import static org.spongepowered.api.command.args.GenericArguments.*;

import com.google.common.collect.Lists;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

@Plugin(
        id = "whois",
        name = "WhoIs",
        authors = "killjoy1221",
        version = "1.0",
        description = "A simple command to give details on a user."
)
public class Whois {

    private static final Text KEY_USER = Text.of("target");

    // FLAGS
    private static final String ALL = "a",
            GAMEMODE = "g", IP = "i", FIRST = "f",
            LAST = "l", WORLD = "w", COORDINATES = "c";

    @Listener
    public void onServerStart(GameStartingServerEvent event) {
        Sponge.getCommandManager().register(this, CommandSpec.builder()
                .description(Text.of("Gets info on the user."))
                .arguments(flags()
                        .permissionFlag("whois.all", ALL, "-all")
                        .permissionFlag("whois.address", IP, "-ip")
                        .flag(FIRST, "-firstjoined")
                        .flag(LAST, "-lastjoined")
                        .flag(WORLD, "-world")
                        .flag(COORDINATES, "-coords")
                        .flag(GAMEMODE, "-gamemode")
                        .buildWith(optional(user(KEY_USER))))
                .permission("whois.command")
                .executor(this::whois)
                .build(), "whois");

    }

    @Nonnull
    private CommandResult whois(CommandSource commandSource, CommandContext commandContext) {
        Optional<User> user = commandContext.getOne(KEY_USER);
        if (!user.isPresent() && commandSource instanceof ConsoleSource) {
            commandSource.sendMessage(Text.of("Console must supply a target"));
            return CommandResult.empty();
        }

        User target = user.orElseGet(() -> (User) commandSource);

        if (!target.equals(commandSource) && !commandSource.hasPermission("whois.other")) {
            commandSource.sendMessage(Text.of(TextColors.RED, "Permission Denied"));
            return CommandResult.empty();
        }

        Text name = Text.of(target.getName());
        Text uuid = Text.of(Text.of(target.getUniqueId()));
        Text online = target.isOnline() ? Text.of(TextColors.GREEN, "ONLINE") : Text.of(TextColors.RED, "OFFLINE");

        // Start forming the output
        List<Text> texts = Lists.newArrayList();
        texts.add(Text.of(TextColors.GRAY, "--------WHOIS--------"));

        texts.add(Text.of(TextColors.YELLOW, "Name: ", TextColors.WHITE, name));
        texts.add(Text.of(TextColors.YELLOW, "UUID: ", TextColors.WHITE, uuid));
        texts.add(Text.of(TextColors.YELLOW, "Status: ", online));

        target.getPlayer()
                .filter(g -> has(commandContext, ALL, GAMEMODE))
                .map(player -> Text.of(player.gameMode().get()))
                .ifPresent(text ->
                        texts.add(Text.of(TextColors.YELLOW, "Game Mode: ", TextColors.WHITE, text))
                );
        target.getPlayer()
                .filter(w -> has(commandContext, ALL, WORLD))
                .map(player -> Text.of(player.getWorld().getName()))
                .ifPresent(text ->
                        texts.add(Text.of(TextColors.YELLOW, "World: ", TextColors.WHITE, text))
                );
        target.getPlayer()
                .filter(c -> has(commandContext, ALL, COORDINATES))
                .map(player -> formatLocation(player.getLocation()))
                .ifPresent(text ->
                        texts.add(Text.of(TextColors.YELLOW, "Coordinates: ", TextColors.WHITE, text))
                );
        target.get(Keys.FIRST_DATE_PLAYED)
                .filter(f -> has(commandContext, ALL, FIRST))
                .map(new TimeSince(commandSource.getLocale()))
                .ifPresent(text ->
                        texts.add(Text.of(TextColors.YELLOW, "First Joined: ", TextColors.WHITE, text))
                );
        target.get(Keys.LAST_DATE_PLAYED)
                .filter(l -> has(commandContext, ALL, LAST))
                .map(new TimeSince(commandSource.getLocale()))
                .ifPresent(text ->
                        texts.add(Text.of(TextColors.YELLOW, "Last Joined: ", TextColors.WHITE, text))
                );
        target.getPlayer()
                .filter(l -> has(commandContext, ALL, IP))
                .map(p -> p.getConnection().getAddress().getHostString())
                .map(Text::of).ifPresent(text ->
                texts.add(Text.of(TextColors.YELLOW, "IP Address: ", TextColors.WHITE, text))
        );

        // send the message
        commandSource.sendMessages(texts);

        return CommandResult.success();
    }

    private static boolean has(CommandContext ctx, String... args) {
        for (String a : args) {
            if (ctx.hasAny(a))
                return true;
        }
        return false;
    }

    private static Text formatLocation(Location<World> loc) {
        return Text.of(loc.getPosition().ceil());
    }

    private static class TimeSince implements Function<Instant, Text> {

        private Locale locale;

        private TimeSince(Locale locale) {
            this.locale = locale;
        }

        @Override
        public Text apply(Instant instant) {
            Text text = Text.of(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(this.locale)
                    .withZone(ZoneId.systemDefault())
                    .format(instant));

            Duration dur = Duration.between(instant, Instant.now()).abs();
            if (dur.getSeconds() < 1)
                return text.concat(Text.of(" (Now)"));
            // seconds
            if (dur.getSeconds() < 60)
                return text.concat(Text.of(" (" + dur.getSeconds() + " seconds ago)"));
            // minutes
            if (dur.toMinutes() < 60)
                return text.concat(Text.of(" (" + dur.toMinutes() + " minutes ago)"));
            // hours
            if (dur.toHours() < 24)
                return text.concat(Text.of(" (" + dur.toHours() + " hours ago)"));
            // days
            if (dur.toDays() < 365)
                return text.concat(Text.of(" (" + dur.toDays() + " days ago)"));

            // Duration doesn't support months or years
            Period per = Period.between(LocalDate.from(instant), LocalDate.now());
            // months
            if (per.getMonths() < 12) {
                return text.concat(Text.of(" (" + per.getMonths() + " months ago)"));
            }
            // years
            return text.concat(Text.of(" (" + per.getYears() + " years ago)"));
        }

    }
}
