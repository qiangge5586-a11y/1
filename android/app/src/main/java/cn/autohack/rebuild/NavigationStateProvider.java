package cn.autohack.rebuild;

/** Read-only provider contract for navigation state presented by the app. */
public interface NavigationStateProvider {
    NavigationState getCurrentState();
}
