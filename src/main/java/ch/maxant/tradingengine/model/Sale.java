package ch.maxant.tradingengine.model;

import java.util.Date;

public class Sale extends IdModel {

    private Date timestamp;
    private Buyer buyer;
    private Seller seller;
    private String productId;
    private double price;
    private int quantity;
    private SalesOrder salesOrder;
    private PurchaseOrder purchaseOrder;

    /**
     * a sale from a seller to a buyer for the given product and price and
     * quantity.
     */
    public Sale(Buyer buyer, Seller seller, String productId, double price,
	    int quantity) {
	this.buyer = buyer;
	this.seller = seller;
	this.productId = productId;
	this.price = price;
	this.quantity = quantity;
	this.timestamp = new Date();
    }

    public Buyer getBuyer() {
	return buyer;
    }

    public double getPrice() {
	return price;
    }

    public String getProductId() {
	return productId;
    }

    public int getQuantity() {
	return quantity;
    }

    public Seller getSeller() {
	return seller;
    }

    public Date getTimestamp() {
	return timestamp;
    }

    public void setSalesOrder(SalesOrder salesOrder) {
	this.salesOrder = salesOrder;
    }

    public SalesOrder getSalesOrder() {
	return salesOrder;
    }

    public void setPurchaseOrder(PurchaseOrder purchaseOrder) {
	this.purchaseOrder = purchaseOrder;
    }

    public PurchaseOrder getPurchaseOrder() {
	return purchaseOrder;
    }

}
