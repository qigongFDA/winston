/**
 * I waive copyright and related rights in the this work worldwide through the CC0 1.0 Universal
 * public domain dedication. https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.winston.server.wws.cmd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import gov.usgs.earthworm.message.TraceBuf;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.winston.db.Channels;
import gov.usgs.volcanoes.winston.db.Data;
import gov.usgs.volcanoes.winston.db.WinstonDatabase;
import gov.usgs.volcanoes.winston.server.MalformedCommandException;
import gov.usgs.volcanoes.winston.server.wws.WinstonConsumer;
import gov.usgs.volcanoes.winston.server.wws.WwsBaseCommand;
import gov.usgs.volcanoes.winston.server.wws.WwsCommandString;
import io.netty.channel.ChannelHandlerContext;

public class GetScnRawCommand extends WwsBaseCommand {

  public GetScnRawCommand() {
    super();
  }

  public void doCommand(ChannelHandlerContext ctx, WwsCommandString cmd)
      throws MalformedCommandException, UtilException {
    if (cmd.length() < 7)
      return; // malformed command

    final String id = cmd.getID();
    final String scnl = cmd.getWinstonSCNL();
    final String s = cmd.getS();
    final String c = cmd.getC();
    final String n = cmd.getN();
    final double t1 = cmd.getT1(false);
    final double t2 = cmd.getT1(false);

    if (scnl == null) {
      throw new MalformedCommandException();
    }



    final Integer chanId;
    try {
      chanId = databasePool.doCommand(new WinstonConsumer<Integer>() {
        public Integer execute(WinstonDatabase winston) throws UtilException {
          return new Channels(winston).getChannelID(scnl);
        }
      });
    } catch (Exception e) {
      throw new UtilException("Unable to get chanId");
    }

    if (chanId == -1) {
      ctx.writeAndFlush(id + " " + id + " " + '0' + " " + s + " " + c + " " + n + " FN\n");
      return;
    }

    final double[] tb;
    try {
      tb = databasePool.doCommand(new WinstonConsumer<double[]>() {
        public double[] execute(WinstonDatabase winston) throws UtilException {
          return new Data(winston).getTimeSpan(chanId);
        }
      });
    } catch (Exception e) {
      throw new UtilException("Unable to get timeSpan.");
    }

    String errorString = null;
    if (t2 < t1) {
      errorString = id + " " + chanId + " " + s + " " + c + " " + n + " " + "FB" + "\n";
    } else if (t2 < tb[0]) {
      errorString = id + " " + chanId + " " + s + " " + c + " " + n + " " + "FL s4 " + "\n";
    } else if (t1 > tb[1]) {
      errorString = id + " " + chanId + " " + s + " " + c + " " + n + " " + "FR s4 " + "\n";
    }

    if (errorString == null) {
      ctx.writeAndFlush(errorString);
      return;
    }

    final List<byte[]> bufs;
    try {
      bufs = databasePool.doCommand(new WinstonConsumer<List<byte[]>>() {
        public List<byte[]> execute(WinstonDatabase winston) throws UtilException {
          return new Data(winston).getTraceBufBytes(scnl, Math.max(t1, tb[0]), Math.min(t2, tb[1]),
              0);
        }
      });
    } catch (Exception e) {
      throw new UtilException("Unable to get chanId");
    }

    final TraceBuf tb0;
    final TraceBuf tbN;
    try {
      tb0 = new TraceBuf(bufs.get(0));
      tbN = new TraceBuf(bufs.get(bufs.size() - 1));
    } catch (IOException e) {
      throw new UtilException("Unable to get bufs.");
    }

    int total = 0;
    for (final byte[] buf : bufs) {
      total += buf.length;
    }

    String hdr = id + " " + chanId + " " + s + " " + c + " " + n + " F " + tb0.dataType() + " "
        + tb0.getStartTime() + " " + tbN.getEndTime() + " " + total + '\n';

    final ByteBuffer bb = ByteBuffer.allocate(total);
    for (final Iterator<byte[]> it = bufs.iterator(); it.hasNext();) {
      bb.put((byte[]) it.next());
    }
    bb.flip();
    ctx.write(hdr);
    ctx.writeAndFlush(bb.array());
  }
}
