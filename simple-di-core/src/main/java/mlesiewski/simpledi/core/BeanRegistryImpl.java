package mlesiewski.simpledi.core;

import mlesiewski.simpledi.core.scopes.ApplicationScope;
import mlesiewski.simpledi.core.scopes.NewInstanceScope;
import mlesiewski.simpledi.core.scopes.Scope;
import mlesiewski.simpledi.core.scopes.SingletonScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Optional;

/** A delegate for {@link BeanRegistry}. Default scope is {@link SingletonScope}. */
class BeanRegistryImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(BeanRegistryImpl.class);

    final HashMap<String, Scope> scopes = new HashMap<>();
    final String DEFAULT_SCOPE;

    /**
     * Constructs a new instance initialized with "appScope" and "toggleScope".
     * Might call {@link Bootstrapper#bootstrap()}.
     */
    BeanRegistryImpl() {
        LOGGER.debug("instantiating BeanRegistryImpl");
        // application scope
        Scope applicationScope = new ApplicationScope();
        register(applicationScope);
        // singleton scope
        Scope singletonScope = new SingletonScope();
        register(singletonScope);
        // new instance scope
        Scope newInstanceScope = new NewInstanceScope();
        register(newInstanceScope);
        // default scope
        DEFAULT_SCOPE = singletonScope.getName();
    }

    /**
     * Registers new scope.
     *
     * @param scope scope to register
     * @throws SimpleDiException if scope is null or is already registered
     */
    public void register(Scope scope) {
        if (scope == null) {
            throw new SimpleDiException("Cannot register null scope");
        }
        if (scopes.containsKey(scope.getName())) {
            throw new SimpleDiException("Scope " + scope.getName() + " is already registered");
        }
        scopes.put(scope.getName(), scope);
    }

    /**
     * Calls {@link #getBean(String, String)}.
     *
     * @return a bean instance
     */
    <T> T getBean(Class aClass, String scopeName) {
        return getBean(aClass.getName(), scopeName);
    }

    /**
     * Calls {@link #getBean(String)}.
     *
     * @return a bean instance
     */
    <T> T getBean(Class aClass) {
        return getBean(aClass.getName());
    }

    /**
     * Calls {@link #getBean(String, String)} with a first scope that has a bean with the name provided.
     *
     * @return a bean instance
     */
    <T> T getBean(String name) {
        Optional<Scope> optional = scopes.values().stream().filter(scope -> scope.hasBean(name)).findFirst();
        if (optional.isPresent()) {
            return optional.get().getBean(name);
        } else {
            throw new SimpleDiException("Cannot find a scope that provides a bean '" + name + "'");
        }
    }

    /** @return a bean instance from the desired scope or default scope as a fallback. */
    <T> T getBean(String beanName, String scopeName) {
        LOGGER.trace("getBean({}, {})", beanName, scopeName);
        Scope scope = getScope(scopeName, true);
        return scope.getBean(beanName);
    }

    /**
     * Registers a {@link BeanProvider} with a {@link Scope}.
     *
     * @param beanProvider     a {@link BeanProvider} instance
     * @param beanProviderName a name under which this provider is going to be registered
     * @param scopeName        name of the {@link Scope} to register this provider with
     * @throws SimpleDiException if beanProviderName or beanProvider are null
     */
    <T> void register(BeanProvider<T> beanProvider, String beanProviderName, String scopeName) throws SimpleDiException {
        LOGGER.trace("register({}, {}, {})", beanProvider, beanProviderName, scopeName);
        if (beanProviderName == null) {
            throw new SimpleDiException("Cannot register a BeanProvider with a null name");
        }
        if (beanProvider == null) {
            throw new SimpleDiException("Cannot register a null BeanProvider under name '" + beanProviderName + "'");
        }
        Scope scope = getScope(scopeName, false);
        scope.register(beanProvider, beanProviderName);
    }

    /** @return a scope with the given name or a default scope as a fallback. */
    private Scope getScope(String scopeName, boolean orDefault) {
        LOGGER.trace("getScope({}, {})", scopeName, orDefault);
        boolean validScopeName = scopes.containsKey(scopeName);
        if (!validScopeName) {
            if (orDefault) {
                LOGGER.trace("scopeName invalid, changing to default {}", DEFAULT_SCOPE);
                scopeName = DEFAULT_SCOPE;
            } else {
                LOGGER.error("no scope registered under the name '{}'", scopeName);
                throw new SimpleDiException("no scope registered under the name " + scopeName);
            }
        }
        return scopes.get(scopeName);
    }

    /**
     * Just calls {@link BeanRegistryImpl#register(BeanProvider, String, String)} with a default scope name.
     *
     * @param beanProvider     a {@link BeanProvider} instance
     * @param beanProviderName a name under which this provider is going to be registered
     */
    <T> void register(BeanProvider<T> beanProvider, String beanProviderName) {
        register(beanProvider, beanProviderName, DEFAULT_SCOPE);
    }

    /**
     * Just calls {@link BeanRegistryImpl#register(BeanProvider, String, String)}.
     *
     * @param beanProvider     a {@link BeanProvider} instance
     * @param beanProviderName a name under which this provider is going to be registered
     * @param scopeName        name of the {@link Scope} to register this provider with
     */
    <T> void register(BeanProvider<T> beanProvider, Class<T> beanProviderName, String scopeName) {
        register(beanProvider, beanProviderName.getName(), scopeName);
    }

    /**
     * Just calls {@link BeanRegistryImpl#register(BeanProvider, String)}.
     *
     * @param beanProvider     a {@link BeanProvider} instance
     * @param beanProviderName a name under which this provider is going to be registered
     */
    <T> void register(BeanProvider<T> beanProvider, Class<T> beanProviderName) {
        register(beanProvider, beanProviderName.getName());
    }

    /** starts eager scopes so that they can be instantiated with their hard dependencies */
    void startEagerScopes() {
        // only one such scope
        scopes.get(ApplicationScope.NAME).start();
    }
}
