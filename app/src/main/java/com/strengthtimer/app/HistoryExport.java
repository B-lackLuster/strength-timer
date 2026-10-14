package com.strengthtimer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Portable, lossless JSON and spreadsheet-friendly UTF-8 CSV snapshots. */
final class HistoryExport {
    static String csv(JSONArray records,TimeZone zone){
        StringBuilder out=new StringBuilder("\uFEFF");
        row(out,"记录编号","训练时间","完成状态","完成组数","有效训练秒数","跳过组数","动作完成组数");
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX",Locale.US);date.setTimeZone(zone);
        for(int n=0;n<records.length();n++){
            JSONObject entry=records.optJSONObject(n);if(entry==null)continue;
            JSONObject counts=entry.optJSONObject("counts");
            row(out,entry.optString("id"),date.format(new Date(entry.optLong("date"))),entry.optBoolean("complete")?"已完成":"提前结束",String.valueOf(entry.optInt("count")),String.valueOf(entry.optLong("seconds")),String.valueOf(entry.optInt("skipped")),counts==null?"{}":counts.toString());
        }
        return out.toString();
    }
    private static void row(StringBuilder out,String... cells){for(int n=0;n<cells.length;n++){if(n>0)out.append(',');out.append('"').append(cells[n].replace("\"","\"\"")).append('"');}out.append("\r\n");}
    static String json(JSONArray records,long exportedAt,TimeZone zone)throws JSONException{
        return new JSONObject().put("format","strength-timer-history").put("version",1).put("exportedAt",exportedAt).put("timeZone",zone.getID()).put("records",records).toString(2);
    }
}
