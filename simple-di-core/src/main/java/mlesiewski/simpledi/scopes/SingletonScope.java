package mlesiewski.simpledi.scopes;

import mlesiewski.simpledi.BeanProvider;
import mlesiewski.simpledi.SimpleDiException;
import mlesiewski.simpledi.annotations.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global singleton scope - beans will be created lazily after this scope was started.
 */
public class SingletonScope extends DefaultScopeImpl {

    /** Ties a {@link Bean} to the application scope. */
    public static final String NAME = "mlesiewski.simpledi.Scope.SING_SCOPE";

    private static final Logger LOGGER = LoggerFactory.getLogger(SingletonScope.class);

    /**
     * Creates new Singleton Scope. Can now register new {@link BeanProvider BeanProvider's} that won't be called
     * until {@link #start()}.
     */
    public SingletonScope() {
        super(NAME, LOGGER);
        start();
    }

    /** @throws SimpleDiException always */
    @Override
    public void end() {
        throw new SimpleDiException(NAME + " cannot be ended");
    }
}
