package au.com.codeka.warworlds.eventbus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import au.com.codeka.common.Log;
import au.com.codeka.warworlds.Util;

/**
 * An implementation of the "event bus" pattern.
 *
 * Basically, you register objects with the event bus that include public methods annotated with
 * the {@link EventHandlerInfo} annotation, and when events are fired, the corresponding
 * implementation is called for you.
 *
 * A couple of features make this more interesting than just holding callbacks directly:
 *
 * 1. The callback objects are held with weak references, so you don't <i>have to</i> unregister
 * them (though it's still a good idea to do it).
 * 2. The {@link EventHandlerInfo} annotation can specify whether you require the callback on the UI
 * thread, or whether you don't care which thread.
 * 3. The implementation handles all the details of handling multiple callbacks and so on.
 */
public class EventBus {
    private static Log log = new Log("EventBus");

    private final List<EventHandlerInfo> mHandlers = new CopyOnWriteArrayList<EventHandlerInfo>();

    /** Subscribe the given object to the event bus. */
    public void register(Object subscriber) {
        // Make sure it's not already subscribed. If it is, that's an error.
        for (EventHandlerInfo handler : mHandlers) {
            Object existingSubscriber = handler.getSubscriber();
            if (existingSubscriber != null && existingSubscriber == subscriber) {
                if (Util.isDebug()) {
                    throw new AlreadyRegisteredException();
                } else {
                    log.error("EventBus.register() called twice on the same object, ignoring second call.");
                }
            }
        }

        int numMethods = 0;
        for (Method method : subscriber.getClass().getDeclaredMethods()) {
            EventHandler eventHandlerAnnotation = method.getAnnotation(EventHandler.class);
            if (eventHandlerAnnotation == null) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1) {
                throw new IllegalArgumentException(
                        "EventHandler method must have exactly one parameter.");
            }
            if (parameters[0].isPrimitive()) {
                throw new IllegalArgumentException(
                        "EventHandler method's parameter must not be a primitive type.");
            }

            boolean callOnUiThread = (eventHandlerAnnotation.thread() == EventHandler.UI_THREAD);

            numMethods ++;
            mHandlers.add(new EventHandlerInfo(parameters[0], method, subscriber, callOnUiThread));
        }

        if (numMethods == 0) {
            // If you don't have any @EventHandler methods, then there's no point registering
            // the object. Usually this means you made a programming error.
            throw new NoEventHandlerMethods();
        }
    }

    /** Unregisters the given object from the event bus. */
    public void unregister(Object subscriber) {
        ArrayList<EventHandlerInfo> kill = new ArrayList<EventHandlerInfo>();
        for (EventHandlerInfo handler : mHandlers) {
            Object existingSubscriber = handler.getSubscriber();
            if (existingSubscriber == null // Remove dead subscribers while we're here....
                    || existingSubscriber == subscriber) {
                kill.add(handler);
            }
        }

        mHandlers.removeAll(kill);
    }

    /** Publish the given event and call all subscribers. */
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null!");
        }
        for (EventHandlerInfo handler : mHandlers) {
            if (handler.handles(event)) {
                handler.call(event);
            }
        }
    }

    public static class AlreadyRegisteredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static class NoEventHandlerMethods extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NoEventHandlerMethods() {
            super("Tried to register a class with no @EventHandler methods on EventBus.");
        }
    }
}
