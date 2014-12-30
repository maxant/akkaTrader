package ch.maxant.tradingengine.model;

import scala.Function0;
import scala.Function0$class;

/** a shim, letting us easily call into the scala world */
public abstract class JFunction0<R> implements Function0<R> {

	@Override
	public byte apply$mcB$sp() {
		return Function0$class.apply$mcB$sp(this);
	}

	@Override
	public char apply$mcC$sp() {
		return Function0$class.apply$mcC$sp(this);
	}

	@Override
	public double apply$mcD$sp() {
		return Function0$class.apply$mcD$sp(this);
	}

	@Override
	public float apply$mcF$sp() {
		return Function0$class.apply$mcF$sp(this);
	}

	@Override
	public int apply$mcI$sp() {
		return Function0$class.apply$mcI$sp(this);
	}

	@Override
	public long apply$mcJ$sp() {
		return Function0$class.apply$mcJ$sp(this);
	}

	@Override
	public short apply$mcS$sp() {
		return Function0$class.apply$mcS$sp(this);
	}

	@Override
	public void apply$mcV$sp() {
		Function0$class.apply$mcV$sp(this);
	}

	@Override
	public boolean apply$mcZ$sp() {
		return Function0$class.apply$mcZ$sp(this);
	}

}
