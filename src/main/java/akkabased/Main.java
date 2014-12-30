package akkabased;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.immutable.IndexedSeq;
import scala.collection.immutable.List;
import scala.concurrent.duration.FiniteDuration;
import spray.can.Http;
import spray.can.Http.Bind;
import spray.can.HttpExt;
import spray.can.server.ServerSettings;
import spray.http.ContentTypes;
import spray.http.HttpHeader;
import spray.http.HttpProtocol;
import spray.http.HttpProtocols;
import spray.http.HttpRequest;
import spray.http.HttpResponse;
import spray.http.StatusCodes;
import spray.io.ServerSSLEngineProvider;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtensionId;
import akka.actor.Props;
import akka.io.IO;
import akka.io.Inet;
import akka.io.Tcp;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.ActorRefRoutee;
import akka.routing.Routee;
import akka.routing.Router;
import akka.routing.RoutingLogic;
import ch.maxant.tradingengine.model.Buyer;
import ch.maxant.tradingengine.model.Constants;
import ch.maxant.tradingengine.model.PurchaseOrder;
import ch.maxant.tradingengine.model.Sale;
import ch.maxant.tradingengine.model.SalesOrder;
import ch.maxant.tradingengine.model.Seller;
import ch.maxant.tradingengine.model.TradingEngine;
import ch.maxant.tradingengine.model.TradingEngine.EventType;
import ch.maxant.tradingengine.model.TradingEngine.Listener;

public class Main {

	public static final ActorSystem system = ActorSystem.create("system");
	private static final Logger LOGGER = LogManager.getLogger(Main.class);
	private static final AtomicInteger numSales = new AtomicInteger();

	/** override using arg0 */
	public static int NUM_KIDS = 4;

	/** override using arg1 */
	public static long DELAY = 3;

	/** override using arg2 */
	public static String DB_HOST = "localhost";

	public static void main(String[] args) {

		if(args.length > 0){
			NUM_KIDS = Integer.parseInt(args[0]);
		}
		if(args.length > 1){
			DELAY = Long.parseLong(args[1]);
		}
		if(args.length > 2){
			DB_HOST = args[2];
		}
		
		ActorRef listener = system.actorOf(Props.create(HttpActor.class), "httpActor"); 
		
		InetSocketAddress endpoint = new InetSocketAddress(3000);
		int backlog = 100;
		List<Inet.SocketOption> options = JavaConversions.asScalaBuffer(new ArrayList<Inet.SocketOption>()).toList();
		Option<ServerSettings> settings = scala.Option.empty();
		ServerSSLEngineProvider sslEngineProvider = null;
		Bind bind = new Http.Bind(listener, endpoint, backlog, options, settings, sslEngineProvider);
		IO.apply(spray.can.Http$.MODULE$, system).tell(bind, ActorRef.noSender());
		
		system.scheduler().schedule(new FiniteDuration(5, TimeUnit.SECONDS), new FiniteDuration(5, TimeUnit.SECONDS), ()->{
			System.out.println(new Date() + " - numSales=" + numSales.get());
		}, system.dispatcher());
	}

	private static class HttpActor extends AbstractActor {

		private static final HttpProtocol HTTP_1_1 = HttpProtocols.HTTP$div1$u002E1();

		@SuppressWarnings("unused")
		// used implicitly by Props
		public HttpActor() {

		    final Router router = partitionAndCreateRouter();
			
			receive(ReceiveBuilder
				.match(HttpRequest.class, r -> {
					int id = Constants.ID.getAndIncrement();
					String path = String.valueOf(r.uri().path());
					if("/sell".equals(path)){
						String productId = r.uri().query().get("productId").get();
						int quantity = Integer.parseInt(r.uri().query().get("quantity").get());
						String who = r.uri().query().get("userId").get();
						double price = Double.parseDouble(r.uri().query().get("price").get());
						SalesOrder so = new SalesOrder(price, productId, quantity, id);
						so.setSeller(new Seller(who));
						router.route(so, self());
						replyOK(id);
					}else if("/buy".equals(path)){
						String productId = r.uri().query().get("productId").get();
						int quantity = Integer.parseInt(r.uri().query().get("quantity").get());
						String who = r.uri().query().get("userId").get();
						PurchaseOrder po = new PurchaseOrder(productId, quantity, 2000.0, id);
						po.setBuyer(new Buyer(who));
						router.route(po, self());
						replyOK(id);
					}else{
						handleUnexpected(r);
					}
				}).match(Tcp.Connected.class, r ->{
					sender().tell(new Http.Register(self(), Http.EmptyFastPath$.MODULE$), self()); //tell that connection will be handled here!
				}).matchAny(o -> {
					if("PeerClosed".equals(String.valueOf(o))){
						//TODO why does this happen? is it normal?
					}else{
						handleUnexpected(o);
					}
				}).build());
		}

		private void handleUnexpected(Object o) {
			List<HttpHeader> headers = JavaConversions.asScalaBuffer(new ArrayList<HttpHeader>()).toList();
			System.err.println("received unknown message: " + (o != null ? o.getClass() : "null") + ", '" + o + "'");
			HttpResponse response = new HttpResponse(StatusCodes.BadRequest(),
					spray.http.HttpEntity$.MODULE$.apply("Unexpected message! " + o), headers, HTTP_1_1);
			sender().tell(response, self());
		}

		/** sends an OK response to the caller */
		private void replyOK(int id) {
			List<HttpHeader> headers = JavaConversions.asScalaBuffer(new ArrayList<HttpHeader>()).toList();
			HttpResponse response = new HttpResponse(StatusCodes.OK(),
					spray.http.HttpEntity$.MODULE$.apply(ContentTypes.application$divjson(), "{msg: 'ok', id: " + id + "}"), headers, HTTP_1_1);
			sender().tell(response, self());
		}

		/** partitions the market by product ID and creates an actor encapsulating an engine, per 
		 * partition. returns a router containing all the kids together with the suitable logic 
		 * to route to the correct engine. */
		private Router partitionAndCreateRouter() {
			Map<String, ActorRef> kids = new HashMap<>();
			java.util.List<Routee> routees = new ArrayList<Routee>();
			int chunk = Constants.PRODUCT_IDS.length / NUM_KIDS;
			for (int i = 0, j = Constants.PRODUCT_IDS.length; i < j; i += chunk) {
			    String[] temparray = Arrays.copyOfRange(Constants.PRODUCT_IDS, i, i + chunk);
			    LOGGER.info("created engine for products " + temparray);
			    ActorRef actor = getContext().actorOf(Props.create(EngineActor.class));
			    getContext().watch(actor);
			    routees.add(new ActorRefRoutee(actor));

			    for (int k = 0; k < temparray.length; k++) {
			    	LOGGER.debug("mapping productId '" + temparray[k] + "' to engine " + i);
			    	kids.put(temparray[k], actor);
			    }
			    LOGGER.info("---started trading");
			    actor.tell(EngineActor.RUN, ActorRef.noSender());
			}			
			Router router = new Router(new PartitioningRoutingLogic(kids), routees);
			return router;
		}
	}

	/** an actor encapsulating an engine which represents a partition of the market */
	private static class EngineActor extends AbstractActor implements Listener {

		private static final String RUN = "RUN";

		private TradingEngine engine = new TradingEngine(DELAY, Constants.TIMEOUT, this);
		
		@SuppressWarnings("unused") //used implicitly by akka
		public EngineActor() {
		    receive(ReceiveBuilder
			      .match(SalesOrder.class, so -> {
					engine.addSalesOrder(so.getSeller().getName(),
						so.getProductId(),
						so.getRemainingQuantity(),
						so.getPrice(), so.getId());
			    }).match(PurchaseOrder.class, po -> {
					engine.addPurchaseOrder(
						po.getBuyer().getName(),
						po.getProductId(),
						po.getRemainingQuantity(), po.getId());
			    }).match(String.class, s -> RUN.equals(s), command -> {
			    	engine.run();
				}).matchAny(o -> {
					LOGGER.error("received unknown message in engine actor " + o);
				}).build());
		}

		@Override
		public void onEvent(EventType type, Object data) {
			
			if(EventType.SALE.equals(type) && ((Sale)data).getSalesOrder().getRemainingQuantity() == 0){
				numSales.getAndIncrement();
			}
			
			//time to rerun the market!
			if(EventType.STOPPED.equals(type)){
				self().tell(RUN, self());
			}
		}
	}
	

	public static class PartitioningRoutingLogic implements RoutingLogic {

		private Map<String, ActorRef> kids;

		public PartitioningRoutingLogic(Map<String, ActorRef> kids) {
			this.kids = kids;
		}

		@Override
		public Routee select(Object message, IndexedSeq<Routee> routees) {

			//find which product ID is relevant here
			String productId = null;
			if(message instanceof PurchaseOrder){
				productId = ((PurchaseOrder) message).getProductId();
			}else if(message instanceof SalesOrder){
				productId = ((SalesOrder) message).getProductId();
			}
			ActorRef actorHandlingProduct = kids.get(productId);

			//no go find the routee for the relevant actor
			for(Routee r : JavaConversions.asJavaIterable(routees)){
				ActorRef a = ((ActorRefRoutee) r).ref(); //cast ok, since the are by definition in this program all routees to ActorRefs
				if(a.equals(actorHandlingProduct)){
					return r;
				}
			}
			
			return akka.routing.NoRoutee$.MODULE$; //none found, return NoRoutee
		}
	}
	
}

//Traversable options = scala.collection.immutable.Nil$.MODULE$;
//}).match(akka.io.Tcp$.MODULE$.PeerClosed$.MODULE$.class, r ->{
//}).match(akka.io.Tcp.Aborted.class, r ->{
