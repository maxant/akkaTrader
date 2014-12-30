package ch.maxant.tradingengine.model;

import scala.Function1;
import scala.Function1$class;

/** a shim, letting us easily call into the scala world */
public abstract class JFunction1<T1, R> implements scala.Function1<T1, R> {
	
	@Override
	public <A> Function1<T1, A> andThen(Function1<R, A> arg0) {
		return Function1$class.andThen(this, arg0);
	}

	@Override
	public double apply$mcDD$sp(double arg0) {
		return Function1$class.apply$mcDD$sp(this, arg0);
	}

	@Override
	public double apply$mcDF$sp(float arg0) {
		return Function1$class.apply$mcDF$sp(this, arg0);
	}

	@Override
	public double apply$mcDI$sp(int arg0) {
		return Function1$class.apply$mcDI$sp(this, arg0);
	}

	@Override
	public double apply$mcDJ$sp(long arg0) {
		return Function1$class.apply$mcDJ$sp(this, arg0);
	}

	@Override
	public float apply$mcFD$sp(double arg0) {
		return Function1$class.apply$mcFD$sp(this, arg0);
	}

	@Override
	public float apply$mcFF$sp(float arg0) {
		return Function1$class.apply$mcFF$sp(this, arg0);
	}

	@Override
	public float apply$mcFI$sp(int arg0) {
		return Function1$class.apply$mcFI$sp(this, arg0);
	}

	@Override
	public float apply$mcFJ$sp(long arg0) {
		return Function1$class.apply$mcFJ$sp(this, arg0);
	}

	@Override
	public int apply$mcID$sp(double arg0) {
		return Function1$class.apply$mcID$sp(this, arg0);
	}

	@Override
	public int apply$mcIF$sp(float arg0) {
		return Function1$class.apply$mcIF$sp(this, arg0);
	}

	@Override
	public int apply$mcII$sp(int arg0) {
		return Function1$class.apply$mcII$sp(this, arg0);
	}

	@Override
	public int apply$mcIJ$sp(long arg0) {
		return Function1$class.apply$mcIJ$sp(this, arg0);
	}

	@Override
	public long apply$mcJD$sp(double arg0) {
		return Function1$class.apply$mcJD$sp(this, arg0);
	}

	@Override
	public long apply$mcJF$sp(float arg0) {
		return Function1$class.apply$mcJF$sp(this, arg0);
	}

	@Override
	public long apply$mcJI$sp(int arg0) {
		return Function1$class.apply$mcJI$sp(this, arg0);
	}

	@Override
	public long apply$mcJJ$sp(long arg0) {
		return Function1$class.apply$mcJJ$sp(this, arg0);
	}

	@Override
	public void apply$mcVD$sp(double arg0) {
		Function1$class.apply$mcVD$sp(this, arg0);
	}

	@Override
	public void apply$mcVF$sp(float arg0) {
		Function1$class.apply$mcVF$sp(this, arg0);
	}

	@Override
	public void apply$mcVI$sp(int arg0) {
		Function1$class.apply$mcVI$sp(this, arg0);
	}

	@Override
	public void apply$mcVJ$sp(long arg0) {
		Function1$class.apply$mcVJ$sp(this, arg0);
	}

	@Override
	public boolean apply$mcZD$sp(double arg0) {
		return Function1$class.apply$mcZD$sp(this, arg0);
	}

	@Override
	public boolean apply$mcZF$sp(float arg0) {
		return Function1$class.apply$mcZF$sp(this, arg0);
	}

	@Override
	public boolean apply$mcZI$sp(int arg0) {
		return Function1$class.apply$mcZI$sp(this, arg0);
	}

	@Override
	public boolean apply$mcZJ$sp(long arg0) {
		return Function1$class.apply$mcZJ$sp(this, arg0);
	}

	@Override
	public <A> Function1<A, R> compose(Function1<A, T1> arg0) {
		return Function1$class.compose(this, arg0);
	}

}
