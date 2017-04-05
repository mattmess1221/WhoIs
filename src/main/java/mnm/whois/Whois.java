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
import org.spongepowered.api.service.ProviderRegistration;
import org.spongepowered.api.service.ban.BanService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.ban.Ban;

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
import java.util.function.Predicate;
import javax.annotation.Nonnull;

@Plugin(
        id = "whois",
        name = "WhoIs",
        authors = "killjoy1221",
//        version = "1.0",
        description = "A simple command to give details on a user.",
        url = "https://github.com/killjoy1221/Whois"
)
public class Whois {

    private static final Text KEY_USER = Text.of("target");

    // FLAGS
    private static final String ALL = "a", BAN = "b",
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
                        .flag(BAN, "-ban")
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
                .filter(has(commandContext, GAMEMODE))
                .map(player -> player.gameMode().get())
                .map(formatText("Game Mode"))
                .ifPresent(texts::add);
        target.getPlayer()
                .filter(has(commandContext, WORLD))
                .map(player -> player.getWorld().getName())
                .map(formatText("World"))
                .ifPresent(texts::add);
        target.getPlayer()
                .filter(has(commandContext, COORDINATES))
                .map(player -> player.getLocation().getPosition().ceil())
                .map(formatText("Coordinates"))
                .ifPresent(texts::add);
        // TODO save this info
        target.get(Keys.FIRST_DATE_PLAYED)
                .filter(has(commandContext, FIRST))
                .map(timeSince(commandSource.getLocale()))
                .map(formatText("First Joined"))
                .ifPresent(texts::add);
        target.get(Keys.LAST_DATE_PLAYED)
                .filter(has(commandContext, LAST))
                .map(timeSince(commandSource.getLocale()))
                .map(formatText("Last Joined"))
                .ifPresent(texts::add);

        target.getPlayer()
                .filter(has(commandContext, IP))
                .map(p -> p.getConnection().getAddress().getHostString())
                .map(formatText("IP Address"))
                .ifPresent(texts::add);

        Optional<BanService> bans = Sponge.getServiceManager().getRegistration(BanService.class)
                .filter(has(commandContext, BAN))
                .map(ProviderRegistration::getProvider);

        bans.ifPresent(banned -> {
            Optional<Ban.Profile> banEntry = banned.getBanFor(target.getProfile());
            // is the user banned?
            texts.add(banEntry.map(ban -> Text.of("User is banned",
                    ban.getBanSource()
                            .map(by -> Text.of(" by ", by))
                            .orElse(null),
                    ban.getReason()
                            .map(reason -> Text.of(" (", reason, ")"))
                            .orElse(null))
            ).orElse(Text.of("User is not banned.")));
            // when was the user banned
            banEntry.ifPresent(ban -> texts.add(Text.of("Banned since ",
                    timeSince(commandSource.getLocale()).apply(ban.getCreationDate()),
                    // does it expire?
                    ban.getExpirationDate()
                            .map(timeSince(commandSource.getLocale()))
                            .map(until -> Text.of(" until", until))
                            .orElse(Text.of(" until forever")))));
        });

        // send the message
        commandSource.sendMessages(texts);

        return CommandResult.success();
    }


    private static <T> Function<T, Text> formatText(String key) {
        return text -> Text.of(TextColors.YELLOW, key + ": ", TextColors.WHITE, text);
    }

    private static <T> Predicate<T> has(CommandContext ctx, String args) {
        return a -> ctx.hasAny(ALL) || ctx.hasAny(args);
    }

    private static Function<Instant, Text> timeSince(final Locale locale) {
        return (instant -> {
            Text text = Text.of(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(locale)
                    .withZone(ZoneId.systemDefault())
                    .format(instant));

            String str = instant.compareTo(Instant.now()) < 0 ? " (%s %s from now)" : " (%s %s ago)";

            Duration dur = Duration.between(instant, Instant.now());


            if (dur.getSeconds() < 1)
                return text.concat(Text.of(" (Now)"));
            // seconds
            if (dur.getSeconds() < 60)
                return text.concat(Text.of(String.format(str, dur.getSeconds(), "seconds")));
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
        });
    }
}
