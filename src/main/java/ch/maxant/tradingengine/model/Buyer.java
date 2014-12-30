package ch.maxant.tradingengine.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.maxant.tradingengine.model.TradingEngine.Listener;

public class Buyer {

    private static final Logger LOGGER = LogManager.getLogger("buyer");

    private String name;
    private List<PurchaseOrder> purchaseOrders = new ArrayList<>();

    public Listener listener;

    public Buyer(String name) {
	this.name = name;
    }

    public String getName() {
	return name;
    }

    public void addPurchaseOrder(PurchaseOrder purchaseOrder) {
	LOGGER.debug(name + " adding " + purchaseOrder);
	purchaseOrder.setBuyer(this);
	this.purchaseOrders.add(purchaseOrder);
    }

    /**
     * @return {Array} all the {@link PurchaseOrder}s for the given product,
     *         where the maximum acceptable price is more than the given price
     */
    public List<PurchaseOrder> getRelevantPurchaseOrders(String productId,
	    double price) {
	return this.purchaseOrders
		.stream()
		.filter(po -> {
		    return po.getProductId().equals(productId)
			    && po.getMaximumAcceptedPrice() >= price;
		}).collect(Collectors.toList());
    }

    public void removePurchaseOrder(PurchaseOrder purchaseOrder) {
	this.purchaseOrders.remove(purchaseOrder);
    }

    public List<PurchaseOrder> removeOutdatedPurchaseOrders(long ageInMs) {
	long now = System.currentTimeMillis();
	List<PurchaseOrder> filter = this.purchaseOrders.stream()
		.filter(po -> {
		    return now - po.getCreated().getTime() > ageInMs;
		}).collect(Collectors.toList());
	this.purchaseOrders.removeAll(filter);
	return filter;
    }

    public List<PurchaseOrder> getPurchaseOrders() {
	return purchaseOrders;
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
	Buyer other = (Buyer) obj;
	if (name == null) {
	    if (other.name != null)
		return false;
	} else if (!name.equals(other.name))
	    return false;
	return true;
    }

    @Override
    public String toString() {
	return "Buyer [name=" + name + "]";
    }

}
