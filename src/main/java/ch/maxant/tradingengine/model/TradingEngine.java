package ch.maxant.tradingengine.model;

import io.netty.buffer.PooledByteBufAllocator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.naming.NamingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import scala.Option;
import scala.collection.JavaConversions;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.util.Try;
import akkabased.Main;

import com.github.mauricio.async.db.Configuration;
import com.github.mauricio.async.db.QueryResult;
import com.github.mauricio.async.db.mysql.MySQLConnection;
import com.github.mauricio.async.db.mysql.pool.MySQLConnectionFactory;
import com.github.mauricio.async.db.pool.ConnectionPool;
import com.github.mauricio.async.db.pool.PoolConfiguration;

// /////////////////////////////////////////////////
// this file contains all classes related to a trading
// engine which uses a market to simulate a trading platform.
// /////////////////////////////////////////////////
public class TradingEngine {

	private static final Logger LOGGER = LogManager.getLogger("tradingEngine");

	public static interface Listener {
		public void onEvent(EventType type, Object data);
	}

	public static enum EventType {
		SALE, PURCHASE, TIMEOUT_SALESORDER, TIMEOUT_PURCHASEORDER, STATS, STOPPED
	}

	private static final String SQL = "INSERT INTO SALES (BUYER_NAME, SELLER_NAME, PRODUCT_ID, PRICE, QUANTITY, PO_ID, SO_ID) VALUES (?, ?, ?, ?, ?, ?, ?)";
	private static final ConnectionPool<MySQLConnection> POOL;
	static {
		Duration connectTimeout = Duration.apply(5.0, TimeUnit.SECONDS);
		Duration testTimeout = Duration.apply(5.0, TimeUnit.SECONDS);
		Configuration configuration = new Configuration("root", Main.DB_HOST, 3306, Option.apply("password"), Option.apply("TRADER"), io.netty.util.CharsetUtil.UTF_8, 16777216, PooledByteBufAllocator.DEFAULT, connectTimeout, testTimeout);
		
		MySQLConnectionFactory factory = new MySQLConnectionFactory(configuration);
		POOL = new ConnectionPool<MySQLConnection>(factory, new PoolConfiguration(1000, 4, 1000, 4000), Main.system.dispatcher());
	}
	
	private Market market = new Market();
	private Map<String, MarketPrice> marketPrices = new HashMap<>();
	private Map<String, List<VolumeRecord>> volumeRecords = new HashMap<>();

	private long delay;

	private long timeout;

	private Listener listener;

	/**
	 * basically a buyer goes into the market at a time where they are happy to
	 * pay the market price. they take it from the cheapest seller (ie the
	 * market price). depending on who is left, the market price goes up or down
	 *
	 * a trading engine has one market place and it controls the frequency of
	 * trades. between trades: - sellers and buyers may enter and exit - all
	 * sales are persisted
	 * 
	 * @param delay
	 *            number of milliseconds between trades
	 * @param timeout
	 *            the number of milliseconds after which incomplete sales or
	 *            purchase orders should be removed and their buyer/seller
	 *            informed of the (partial) failure.
	 * @throws NamingException
	 */
	public TradingEngine(long delay, long timeout, Listener listener) {
		this(delay, timeout, listener, false);
	}

	public TradingEngine(long delay, long timeout, Listener listener, boolean runInActorMode) {

		this.delay = delay;
		this.timeout = timeout;
		this.listener = listener;
		LOGGER.debug("market is opening for trading!");
	}

	public void run() {

		LOGGER.debug("\n\n------------------------------- trading...-------------------------");
		long start = System.currentTimeMillis();

		prepareMarket();

		List<Sale> sales = market.trade();
		LOGGER.info("trading completed");

		noteMarketPricesAndVolumes(sales);

		persistSales(sales, t -> {
			if(t != null){
				LOGGER.error("failed to persist sales: " + sales, t);
			}else{
				LOGGER.info("persisting completed, notifying involved parties...");
				sales.stream().forEach(sale -> {
					if (sale.getBuyer().listener != null)
						sale.getBuyer().listener.onEvent(EventType.PURCHASE, sale);
					if (sale.getSeller().listener != null)
						sale.getSeller().listener.onEvent(EventType.SALE, sale);
				});
				if (!sales.isEmpty()) {
					LOGGER.error("trading of " + sales.size() + " sales completed and persisted in "
							+ (System.currentTimeMillis() - start) + "ms");
				} else {
					LOGGER.info("no trades...");
				}

				// debug(self.market, 10, false);
				if (listener != null)
					this.updateMarketVolume(null); // removes outdated data
				listener.onEvent(EventType.STATS, new Object[] { market.getMarketInfo(), this.marketPrices,
						this.volumeRecords });
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}
			listener.onEvent(EventType.STOPPED, null);
		});
	}

	/**
	 * @method @return a VolumeRecord, just with no timestamp. properties are
	 *         total in last minute.
	 */
	public VolumeRecord getCurrentVolume(String productId) {
		List<VolumeRecord> vrs = this.volumeRecords.get(productId);
		if (vrs != null) {
			long now = System.currentTimeMillis();
			vrs = vrs.stream().filter(vr -> {
				return now - vr.timestamp.getTime() < 1000 * 10;
			}).collect(Collectors.toList()); // remove old
			this.volumeRecords.put(productId, vrs); // ensure records contains
			// most up to date

			// aggregate
			VolumeRecord vr = new VolumeRecord(productId, 0, 0, null, 0);
			vr = vrs.stream().reduce(vr, VolumeRecord::add);
			return vr;
		} else {
			return new VolumeRecord(productId, 0, 0, null, 0);
		}
	}

	/** @method @return the last known price */
	public MarketPrice getCurrentMarketPrice(String productId) {
		return this.marketPrices.get(productId);
	}

	// handles timed out orders
	private void prepareMarket() {

		// handle timeouted sales orders
		market.getSellers().forEach(seller -> {
			List<SalesOrder> incompleteSOs = seller.removeOutdatedSalesOrders(timeout);
			incompleteSOs.forEach(so -> {
				if (so.getSeller().listener != null)
					so.getSeller().listener.onEvent(EventType.TIMEOUT_SALESORDER, so);
				else
					LOGGER.debug("incomplete SO: " + so);
			});
		});

		// handle timeouted purchase orders
		market.getBuyers().forEach(buyer -> {
			List<PurchaseOrder> incompletePOs = buyer.removeOutdatedPurchaseOrders(timeout);
			incompletePOs.forEach(po -> {
				if (po.getBuyer().listener != null)
					po.getBuyer().listener.onEvent(EventType.TIMEOUT_PURCHASEORDER, po);
				else
					LOGGER.debug("incomplete PO: " + po);
			});
		});
	}

	private void persistSales(List<Sale> sales, final PersistenceComplete f) {
		if (!sales.isEmpty()) {
			LOGGER.info("preparing to persist sales");

			final AtomicInteger count = new AtomicInteger(sales.size());
			sales.forEach(sale -> {
				List values = Arrays.asList(sale.getBuyer().getName(), 
											sale.getSeller().getName(),
											sale.getProductId(),
											sale.getPrice(),
											sale.getQuantity(),
											sale.getPurchaseOrder().getId(),
											sale.getSalesOrder().getId());
				
				Future<QueryResult> sendQuery = POOL.sendPreparedStatement(SQL, JavaConversions.asScalaBuffer(values));
				sendQuery.onComplete(new JFunction1<Try<QueryResult>, Void>() {
					@Override
					public Void apply(Try<QueryResult> t) {
						//TODO seems this isnt called after results come, rather when app closes?
						//POOL.close();
						
						if(t.isSuccess()){
							QueryResult qr = t.get();
							//the query result doesnt contain auto generated IDs! library seems immature...
							//ResultSet rs = qr.rows().get();
							//RowData rd = rs.apply(Integer.valueOf(0));
							//sale.setId((Integer)rd.apply(0)); 
						}
						
						if(count.decrementAndGet() == 0){
							if(t.isSuccess()){
								f.apply(null);
							}else{
								f.apply(t.failed().get());
							}
							
						}

						return null; //coz of Void
					}
				}, Main.system.dispatcher());
			});
		}else{
			f.apply(null); //nothing to do, so continue immediately
		}
	}

	private void noteMarketPricesAndVolumes(List<Sale> sales) {
		sales.forEach(sale -> {
			updateMarketPrice(sale);
			updateMarketVolume(sale);
		});
	}

	public static class MarketPrice {
		private String productId;
		private double price;
		private Date timestamp;

		public MarketPrice(String productId, double price, Date timestamp) {
			this.productId = productId;
			this.price = price;
			this.timestamp = timestamp;
		}

		public double getPrice() {
			return price;
		}

		public String getProductId() {
			return productId;
		}

		public Date getTimestamp() {
			return timestamp;
		}
	}

	private void updateMarketPrice(Sale sale) {
		MarketPrice mp = marketPrices.get(sale.getProductId());
		if (mp == null || (mp != null && mp.getTimestamp().getTime() < sale.getTimestamp().getTime())) {
			// set price if none is known, or replace price if its older than
			// current price
			marketPrices.put(sale.getProductId(),
					new MarketPrice(sale.getProductId(), sale.getPrice(), sale.getTimestamp()));
		}
	}

	public static class VolumeRecord {
		public static final VolumeRecord EMPTY = new VolumeRecord(null, 0, 0, null, 0);
		public String productId;
		public int numberOfSales;
		public double turnover;
		public Date timestamp;
		public int count;

		public VolumeRecord(String productId, int numberOfSales, double turnover, Date timestamp, int count) {
			this.productId = productId;
			this.numberOfSales = numberOfSales;
			this.turnover = turnover;
			this.timestamp = timestamp;
			this.count = count;
		}

		public static VolumeRecord add(VolumeRecord a, VolumeRecord b) {
			return new VolumeRecord(b.productId, a.numberOfSales + b.numberOfSales, a.turnover + b.turnover, null,
					a.count + b.count);
		}

		public static VolumeRecord aggregate(List<VolumeRecord> vrs) {
			VolumeRecord vr = EMPTY;
			vr = vrs.stream().reduce(vr, VolumeRecord::add);
			return vr;

		}
	}

	private void updateMarketVolume(Sale sale) {
		// //////////////
		// remove old ones
		// //////////////
		Map<String, List<VolumeRecord>> newVolumeRecords = new HashMap<>();
		long now = System.currentTimeMillis();
		volumeRecords.forEach((k, v) -> {
			List<VolumeRecord> vrs = v.stream().filter(vr -> {
				return now - vr.timestamp.getTime() < 1000 * 10;
			}).collect(Collectors.toList()); // remove older than 10 secs
				newVolumeRecords.put(k, vrs);
			});
		volumeRecords = newVolumeRecords; // replace the old ones

		// //////////////
		// add new data
		// //////////////
		if (sale != null) {
			List<VolumeRecord> vrs = volumeRecords.get(sale.getProductId());
			if (vrs == null) {
				vrs = new ArrayList<>();
			}
			vrs.add(new VolumeRecord(sale.getProductId(), sale.getQuantity(), sale.getQuantity() * sale.getPrice(),
					sale.getTimestamp(), 1)); // scale up to "per minute"
			volumeRecords.put(sale.getProductId(), vrs); // replace with old one
		}
	}

	public PurchaseOrder addPurchaseOrder(String who, String productId, int quantity, int id) {

		Buyer buyer = new Buyer(who);
		if (!this.market.getBuyers().contains(buyer)) {
			LOGGER.debug("buyer named " + who + " doesnt exist -> adding a new one");
			this.market.addBuyer(buyer);
			buyer.listener = listener;
		} else {
			// swap temp buyer with the actual one in the market
			buyer = this.market.getBuyers().get(this.market.getBuyers().indexOf(buyer));
		}
		PurchaseOrder po = new PurchaseOrder(productId, quantity, 9999.9, id);
		buyer.addPurchaseOrder(po);
		return po;
	}

	public SalesOrder addSalesOrder(String who, String productId, int quantity, double price, int id) {

		Seller seller = new Seller(who);
		if (!this.market.getSellers().contains(seller)) {
			LOGGER.debug("seller named " + who + " doesnt exist -> adding a new one");
			this.market.addSeller(seller);
			seller.listener = listener;
		} else {
			// swap temp seller with the actual one in the market
			seller = this.market.getSellers().get(this.market.getSellers().indexOf(seller));
		}
		SalesOrder so = new SalesOrder(price, productId, quantity, id);
		seller.addSalesOrder(so);
		return so;
	}

	private static interface PersistenceComplete {
		void apply(Throwable failure);
	}
}
