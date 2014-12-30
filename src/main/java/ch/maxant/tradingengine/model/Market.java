package ch.maxant.tradingengine.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A market contains 0..n sellers. A seller has 0..i sales orders each of which
 * contains a quantity of a product at a certain price. The seller is prepared
 * to sell their product for that price. The market also contains 0..m buyers.
 * Each buyer has 0..j purchase orders. A purchase order is for a given product
 * and quantity. The purchase price may not exceed the given price. The market
 * works by continuously looping through trade sittings. See the {@link #trade}
 * method.
 */
public class Market {

    private static final Logger LOGGER = LogManager.getLogger("market");

    private List<Seller> sellers = new ArrayList<>();
    private List<Buyer> buyers = new ArrayList<>();

    private MarketInfo marketInfo;

    public void addSeller(Seller seller) {
	this.sellers.add(seller);
    }

    public void addBuyer(Buyer buyer) {
	this.buyers.add(buyer);
    }

    /**
     * At a single trade sitting, the following happens: 1) find all products
     * available (on offer by sellers) 2) for each product: 2a) for each buyer
     * interested in that product: 2ai) find the seller with the cheapest price
     * for the current product 2aii) if such a seller exists, create a sale,
     * otherwise nobody is selling the product anymore, so skip to next product.
     *
     * The point is that a buyer always goes to the cheapest seller, even if
     * that seller doesnt have enough quantity. A buyer who wants more has to
     * wait until the next trading session to find the next most suitable
     * seller.
     *
     * @return {Array} array of {@link Sale}s in this trade
     */
    public List<Sale> trade() {
	List<Sale> sales = new ArrayList<>();

	Set<String> productsInMarket = getProductsInMarket();

	this.collectMarketInfo();

	// trade each product in succession
	productsInMarket
		.stream()
		.forEach(
			productId -> {

			    MutableBoolean soldOutOfProduct = new MutableBoolean(
				    false);
			    LOGGER.debug("trading product " + productId);
			    List<Buyer> buyersInterestedInProduct = getBuyersInterestedInProduct(productId);
			    if (buyersInterestedInProduct.size() == 0) {
				LOGGER.info("no buyers interested in product "
					+ productId);
			    } else {
				buyersInterestedInProduct.forEach(buyer -> {
				    if (soldOutOfProduct.isFalse()) {
					LOGGER.debug("  buyer "
						+ buyer.getName()
						+ " is searching for product "
						+ productId);
					// select the cheapest seller
					Optional<Seller> cheapestSeller = sellers
						.stream()
						.filter(seller -> {
						    return seller
							    .hasProduct(productId);
						})
						.sorted((s1, s2) -> Double
							.compare(
								s1.getCheapestSalesOrder(
									productId)
									.getPrice(),
								s2.getCheapestSalesOrder(
									productId)
									.getPrice()))
						.findFirst();
					if (cheapestSeller.isPresent()) {
					    LOGGER.debug("    cheapest seller is "
						    + cheapestSeller.get()
							    .getName());
					    List<Sale> newSales = createSale(
						    buyer,
						    cheapestSeller.get(),
						    productId);
					    sales.addAll(newSales);
					    LOGGER.debug("    sales completed");
					} else {
					    LOGGER.warn("    market sold out of product "
						    + productId);
					    soldOutOfProduct.setTrue();
					}
				    }
				});
			    }
			});

	return sales;
    };

    public void collectMarketInfo() {
	this.marketInfo = new MarketInfo();

	this.marketInfo.pos = buyers.stream().map(buyer -> {
	    return buyer.getPurchaseOrders();
	}).flatMap(l -> l.stream()).collect(Collectors.groupingBy(po -> {
	    return po.getProductId();
	}));

	this.marketInfo.sos = sellers.stream().map(seller -> {
	    return seller.getSalesOrders();
	}).flatMap(l -> l.stream()).collect(Collectors.groupingBy(so -> {
	    return so.getProductId();
	}));

	/*
	 * for(String productId : this.marketInfo.pos.keySet()){
	 * this.marketInfo.pos.put(productId) =
	 * this.marketInfo.pos[productId].length; } for(var productId :
	 * this.marketInfo.sos){ this.marketInfo.sos[productId] =
	 * this.marketInfo.sos[productId].length; }
	 */
    };

    /**
     * creates a sale if the prices is within the buyers budget. iterates all of
     * the buyers purchase wishes for the given product so long as the seller
     * still has the product.
     * 
     * @return {Array} array of new {@link Sale}s, after having removed a
     *         quantity of the product from the seller/buyer.
     */
    public List<Sale> createSale(Buyer buyer, Seller seller, String productId) {
	SalesOrder cheapestSalesOrder = seller.getCheapestSalesOrder(productId);
	LOGGER.debug("cheapest sales order " + cheapestSalesOrder);

	// find the buyers purchase orders, where the po.price =>
	// cheapestSalesOrder.price
	// create a sale for each buyer's purchase order
	// until either the seller has no more stock at this price
	// or the buyer has bought all they want

	List<PurchaseOrder> purchaseOrders = buyer.getRelevantPurchaseOrders(
		productId, cheapestSalesOrder.getPrice());
	LOGGER.debug("relevant purchase orders: " + purchaseOrders);

	List<Sale> sales = new ArrayList<>();
	purchaseOrders.stream().forEach(
		purchaseOrder -> {
		    int quantity = Math.min(
			    cheapestSalesOrder.getRemainingQuantity(),
			    purchaseOrder.getRemainingQuantity());
		    LOGGER.debug("quantity " + quantity + " for PO: "
			    + purchaseOrder);
		    if (quantity > 0) {
			Sale sale = new Sale(buyer, seller, productId,
				cheapestSalesOrder.getPrice(), quantity);

			// add PO and SO for events
			sale.setPurchaseOrder(purchaseOrder);
			sale.setSalesOrder(cheapestSalesOrder);
			sales.add(sale);
			LOGGER.debug("created sale: " + sale);

			// adjust quantities purchaseOrder.remainingQuantity -=
			// quantity;
			cheapestSalesOrder.reduceRemainingQuantity(quantity);

			// remove completed purchase wishes
			if (purchaseOrder.getRemainingQuantity() == 0) {
			    LOGGER.debug("PO complete: " + sale);
			    buyer.removePurchaseOrder(purchaseOrder);
			}
		    }
		});

	// remove completed sales orders
	if (cheapestSalesOrder.getRemainingQuantity() == 0) {
	    LOGGER.debug("SO complete: " + cheapestSalesOrder);
	    seller.removeSalesOrder(cheapestSalesOrder);
	}

	return sales;
    }

    /**
     * @return all buyers in the market who have a purchase order for the given
     *         product
     */
    public List<Buyer> getBuyersInterestedInProduct(final String productId) {
	return this.buyers.stream().filter(buyer -> {
	    return buyer.getPurchaseOrders().stream().anyMatch(po -> {
		return po.getProductId().equals(productId);
	    });
	}).collect(Collectors.toList());
    }

    /** @return all product IDs that are for sale in the market */
    public Set<String> getProductsInMarket() {
	// TODO use flatmap, but also in js too!
	Set<String> productsInMarket = new HashSet<>();
	sellers.forEach(seller -> {
	    seller.getSalesOrders().stream().forEach(salesOrder -> {
		productsInMarket.add(salesOrder.getProductId());
	    });
	});
	return productsInMarket;
    }

    public static class MarketInfo {
	public Map<String, List<PurchaseOrder>> pos;
	public Map<String, List<SalesOrder>> sos;
    }

    public MarketInfo getMarketInfo() {
	return marketInfo;
    }

    public List<Seller> getSellers() {
	return sellers;
    }

    public List<Buyer> getBuyers() {
	return buyers;
    }
}
