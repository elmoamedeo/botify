package net.robinfriedli.botify.discord.listeners;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.concurrent.EventHandlerPool;
import org.springframework.stereotype.Component;

@Component
public class EventWaiter extends ListenerAdapter {

    @SuppressWarnings("rawtypes")
    private final Multimap<Class<? extends GenericEvent>, AwaitedEvent> awaitedEventMap = HashMultimap.create();

    @SuppressWarnings("unchecked")
    @Override
    public void onGenericEvent(@Nonnull GenericEvent event) {
        EventHandlerPool.POOL.execute(() -> Lists.newArrayList(awaitedEventMap.get(event.getClass()))
            .stream()
            .filter(awaitedEvent -> awaitedEvent.getFilterPredicate().test(event))
            .forEach(awaitedEvent -> {
                awaitedEvent.getCompletableFuture().complete(event);
                awaitedEventMap.remove(awaitedEvent.getEventType(), awaitedEvent);
            }));
    }

    public <E extends GenericEvent> E awaitEvent(Class<E> eventType, Predicate<E> predicate, long timeout, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
        CompletableFuture<E> completableFuture = awaitEvent(eventType, predicate);
        try {
            return completableFuture.get(timeout, timeUnit);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public <E extends GenericEvent> CompletableFuture<E> awaitEvent(Class<E> eventType, Predicate<E> predicate) {
        AwaitedEvent<E> awaitedEvent = new AwaitedEvent<>(eventType, predicate);
        awaitedEventMap.put(eventType, awaitedEvent);
        return awaitedEvent.getCompletableFuture();
    }

    private static class AwaitedEvent<E extends GenericEvent> {

        private final Class<E> eventType;
        private final Predicate<E> filterPredicate;
        private final CompletableFuture<E> completableFuture;

        private AwaitedEvent(Class<E> eventType, Predicate<E> filterPredicate) {
            this.eventType = eventType;
            this.filterPredicate = filterPredicate;
            completableFuture = new CompletableFuture<>();
        }

        public Class<E> getEventType() {
            return eventType;
        }

        public Predicate<E> getFilterPredicate() {
            return filterPredicate;
        }

        public CompletableFuture<E> getCompletableFuture() {
            return completableFuture;
        }
    }

}
