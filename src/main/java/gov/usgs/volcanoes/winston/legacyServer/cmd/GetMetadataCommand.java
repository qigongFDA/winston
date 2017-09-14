package gov.usgs.volcanoes.winston.legacyServer.cmd;

import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import gov.usgs.net.NetTools;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.TimeSpan;
import gov.usgs.volcanoes.winston.Channel;
import gov.usgs.volcanoes.winston.Instrument;
import gov.usgs.volcanoes.winston.db.Channels;
import gov.usgs.volcanoes.winston.db.WinstonDatabase;
import gov.usgs.volcanoes.winston.legacyServer.WWS;
import gov.usgs.volcanoes.winston.legacyServer.WWSCommandString;

/**
 *
 * @author Dan Cervelli
 */
public class GetMetadataCommand extends BaseCommand {
  private final Channels channels;

  public GetMetadataCommand(final NetTools nt, final WinstonDatabase db, final WWS wws) {
    super(nt, db, wws);
    channels = new Channels(db);
  }

  private String escape(final String s) {
    if (s == null)
      return "";
    return s.replaceAll(",", "\\\\c").replaceAll("\n", "\\\\n");
  }

  private void appendList(final StringBuilder sb, final String name, final List<String> list) {
    sb.append(name);
    sb.append("=");
    if (list == null)
      return;
    for (final String value : list) {
      sb.append(escape(value));
      sb.append("\\c");
    }
  }

  private void appendMap(final StringBuilder sb, final Map<String, String> map) {
    if (map == null)
      return;

    for (final String key : map.keySet()) {
      final String value = map.get(key);
      sb.append(escape(key));
      sb.append("=");
      sb.append(escape(value));
      sb.append(",");
    }
  }

  private void getInstrumentMetadata(final WWSCommandString cmd, final SocketChannel channel) {
    final List<Instrument> insts = channels.getInstruments();
    final StringBuilder sb = new StringBuilder(insts.size() * 60);
    sb.append(String.format("%s %d\n", cmd.getID(), insts.size()));
    for (final Instrument inst : insts) {
      sb.append("name=");
      sb.append(escape(inst.name));
      sb.append(",");
      sb.append("description=");
      sb.append(escape(inst.description));
      sb.append(",");
      sb.append("longitude=");
      sb.append(inst.longitude);
      sb.append(",");
      sb.append("latitude=");
      sb.append(inst.latitude);
      sb.append(",");
      sb.append("height=");
      sb.append(inst.height);
      sb.append(",");
      sb.append("timezone=");
      sb.append(inst.timeZone);
      sb.append(",");
      appendMap(sb, inst.metadata);
      sb.append("\n");
    }
    netTools.writeString(sb.toString(), channel);
  }

  private void getChannelMetadata(final WWSCommandString cmd, final SocketChannel channel) {
    final List<Channel> chs = channels.getChannels(true);
    final StringBuilder sb = new StringBuilder(chs.size() * 60);
    sb.append(String.format("%s %d\n", cmd.getID(), chs.size()));
    for (final Channel ch : chs) {
      TimeSpan timeSpan = ch.timeSpan;
      sb.append("channel=");
      sb.append(ch.scnl.toString(" "));
      sb.append(",");
      sb.append("instrument=");
      sb.append(escape(ch.instrument.name));
      sb.append(",");
      sb.append("startTime=");
      sb.append(timeOrMaxDays(J2kSec.asEpoch(timeSpan.startTime)));
      sb.append(",");
      sb.append("endTime=");
      sb.append(timeOrMaxDays(J2kSec.asEpoch(timeSpan.endTime)));
      sb.append(",");
      sb.append("alias=");
      sb.append(escape(ch.alias));
      sb.append(",");
      sb.append("unit=");
      sb.append(escape(ch.unit));
      sb.append(",");
      sb.append("linearA=");
      sb.append(ch.linearA);
      sb.append(",");
      sb.append("linearB=");
      sb.append(ch.linearB);
      sb.append(",");
      appendList(sb, "groups", ch.groups);
      sb.append(",");
      appendMap(sb, ch.metadata);
      sb.append("\n");
    }
    netTools.writeString(sb.toString(), channel);
  }

  public void doCommand(final Object info, final SocketChannel channel) {
    final WWSCommandString cmd = new WWSCommandString((String) info);
    final String[] ss = cmd.getCommandSplits();
    if (ss.length <= 2)
      return;

    if (ss[2].equals("INSTRUMENT"))
      getInstrumentMetadata(cmd, channel);
    else if (ss[2].equals("CHANNEL"))
      getChannelMetadata(cmd, channel);

    wws.log(Level.FINER, "GETMETADATA.", channel);
  }
}
