package de.srsoftware.umbrella;

public class RedirectException extends Exception {

	private static final long serialVersionUID = 684302573430847538L;

	public RedirectException(String redirect) {
		super(redirect);
	}

}
