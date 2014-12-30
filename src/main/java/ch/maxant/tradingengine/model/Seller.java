package ch.maxant.tradingengine.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.maxant.tradingengine.model.TradingEngine.Listener;

public class Seller {

    private static final Logger LOGGER = LogManager.getLogger("seller");

    private List<SalesOrder> salesOrders = new ArrayList<>();
    private String name;

    public Listener listener;

    public Seller(String name) {
	this.name = name;
    }

    public void addSalesOrder(SalesOrder salesOrder) {
	LOGGER.debug(name + " adding " + salesOrder);
	salesOrder.setSeller(this);
	this.salesOrders.add(salesOrder);
    }

    public boolean hasProduct(String productId) {
	return this.salesOrders.stream().anyMatch(so -> {
	    return so.getProductId().equals(productId);
	});
    }

    /**
     * @return {SalesOrder} the sales order for the given product that has the
     *         lowest price
     */
    public SalesOrder getCheapestSalesOrder(String productId) {
	return this.salesOrders.stream().filter(so -> {
	    return so.getProductId().equals(productId);
	}).sorted((o1, o2) -> Double.compare(o1.getPrice(), o2.getPrice()))
		.findFirst().get();
    }

    public void removeSalesOrder(SalesOrder salesOrder) {
	this.salesOrders = this.salesOrders.stream().filter(so -> {
	    return !salesOrder.equals(so);
	}).collect(Collectors.toList());
    }

    /** @return the out of date ones */
    public List<SalesOrder> removeOutdatedSalesOrders(long ageInMs) {
	long now = System.currentTimeMillis();

	Map<Boolean, List<SalesOrder>> partitioned = salesOrders.stream()
		.collect(Collectors.groupingBy(so -> {
		    return now - so.getCreated().getTime() > ageInMs;
		}));

	this.salesOrders = ObjectUtils.defaultIfNull(
		partitioned.get(Boolean.FALSE),
		new ArrayList<SalesOrder>());
	return ObjectUtils.defaultIfNull(partitioned.get(Boolean.TRUE),
		new ArrayList<SalesOrder>());
    }

    public List<SalesOrder> getSalesOrders() {
	return salesOrders;
    }

    public String getName() {
	return name;
    }

    @Override
    public int hashCode() {
	final int prime = 31;
	int result = 1;
	result = prime * result + ((name == null) ? 0 : name.hashCode());
	return result;
    }

    @Override
    public boolean equals(Object obj) {
	if (this == obj)
	    return true;
	if (obj == null)
	    return false;
	if (getClass() != obj.getClass())
	    return false;
	Seller other = (Seller) obj;
	if (name == null) {
	    if (other.name != null)
		return false;
	} else if (!name.equals(other.name))
	    return false;
	return true;
    }

    @Override
    public String toString() {
	return "Seller [name=" + name + "]";
    }

}
