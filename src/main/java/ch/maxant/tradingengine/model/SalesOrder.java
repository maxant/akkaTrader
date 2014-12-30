package ch.maxant.tradingengine.model;

import java.util.Date;

public class SalesOrder extends IdModel {

    private double price;
    private String productId;
    private int remainingQuantity;
    private int originalQuantity;
    private Date created;
    private Seller seller;

    /**
     * an order to sell a given quantity of a product at a given price
     */
    public SalesOrder(double price, String productId, int quantity, int id) {
	this.price = price;
	this.productId = productId;
	this.remainingQuantity = quantity;
	this.originalQuantity = quantity;
	this.created = new Date();
	setId(id);
    }

    public Date getCreated() {
	return created;
    }

    public int getRemainingQuantity() {
	return remainingQuantity;
    }

    public String getProductId() {
	return productId;
    }

    public double getPrice() {
	return price;
    }

    public int getOriginalQuantity() {
	return originalQuantity;
    }

    public Seller getSeller() {
	return seller;
    }

    public void setSeller(Seller seller) {
	this.seller = seller;
    }

    public void reduceRemainingQuantity(double quantity) {
	this.remainingQuantity -= quantity;
    }

}
