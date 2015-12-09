package gov.usgs.volcanoes.winston.server.cmd.http.fdsn;

/**
 *
 * @author Tom Parker
 *
 */
public class FdsnException extends Exception {

  public final int code;
  public final String message;

  public FdsnException(final int code, final String message) {
    this.code = code;
    this.message = message;
  }
}