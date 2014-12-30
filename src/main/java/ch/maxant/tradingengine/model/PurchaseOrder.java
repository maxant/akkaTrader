package ch.maxant.tradingengine.model;

import java.util.Date;

public class PurchaseOrder extends IdModel {

    private String productId;
    private int remainingQuantity;
    private int originalQuantity;
    private double maximumAcceptedPrice;
    private Date created;
    private Buyer buyer;

    public PurchaseOrder(String productId, int quantity,
	    double maximumAcceptedPrice, int id) {
	this.productId = productId;
	this.remainingQuantity = quantity;
	this.originalQuantity = quantity;
	this.maximumAcceptedPrice = maximumAcceptedPrice;
	this.created = new Date();
	setId(id);
    }

    public void setBuyer(Buyer buyer) {
	this.buyer = buyer;
    }

    public String getProductId() {
	return productId;
    }

    public Buyer getBuyer() {
	return buyer;
    }

    public Date getCreated() {
	return created;
    }

    public double getMaximumAcceptedPrice() {
	return maximumAcceptedPrice;
    }

    public int getOriginalQuantity() {
	return originalQuantity;
    }

    public int getRemainingQuantity() {
	return remainingQuantity;
    }

}
