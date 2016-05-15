package mlesiewski.simpledi.model;

/**
 * An entity representing BeanProvider that delegates calls to a BeanProducer method.
 */
public class ProducedBeanProviderEntity extends BeanProviderEntity implements GeneratedCode {

    private final BeanEntity beanProducer;
    private final String producerMethod;

    /**
     * @param producedBean a bean that is going to be produced by this provider
     * @param beanProducer a bean that is going to be a delegate for bean production call
     * @param producerMethod name of the method of beanProducer that produces producedBean instances
     */
    public ProducedBeanProviderEntity(BeanEntity producedBean, BeanEntity beanProducer, String producerMethod) {
        super(producedBean.packageName(), (producedBean.defaultName() ? producedBean.simpleName() : producedBean.name()) + "Wrapper", producedBean);
        this.beanProducer = beanProducer;
        this.producerMethod = producerMethod;
    }

    public String producerMethod() {
        return producerMethod;
    }

    public BeanEntity beanProducer() {
        return beanProducer;
    }
}