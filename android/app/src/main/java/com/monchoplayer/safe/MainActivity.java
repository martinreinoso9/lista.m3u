package com.monchoplayer.safe;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
    private static final String BASE="https://raw.githubusercontent.com/monchotv/MonchoApps/main/";
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<Channel> channels=new ArrayList<>(), shown=new ArrayList<>();
    private SharedPreferences prefs;
    private Set<String> favorites;
    private Spinner groups;
    private EditText search;
    private TextView status, now;
    private ListView list;
    private ArrayAdapter<String> adapter;
    private PlayerView video;
    private ExoPlayer player;
    private LinearLayout controls, browser;
    private Channel current;
    private boolean loading, fullscreen, stopped, destroyed;
    private int generation;
    private static class Channel {
        String name, group, url, type;
        boolean drm;
        final HashMap<String,String> headers=new HashMap<>();
        String key(){return name+"\n"+group;}
        boolean complete(){return url.startsWith("https://")||url.startsWith("http://");}
        String label(){return name+(drm?" [DRM]":"")+(type.equals("WEBVIEW")?" [WEB]":"")+(!complete()?" [URL INCOMPLETA]":"");}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("player",MODE_PRIVATE);
        favorites=new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,12,16,8);root.setBackgroundColor(Color.rgb(14,22,36));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(16+insets.getSystemWindowInsetLeft(),12+insets.getSystemWindowInsetTop(),16+insets.getSystemWindowInsetRight(),8+insets.getSystemWindowInsetBottom());return insets;});
        controls=new LinearLayout(this); controls.setOrientation(1);root.addView(controls);
        TextView title=new TextView(this);title.setText("MONCHO PLAYER");title.setTextSize(23);title.setTextColor(Color.rgb(79,224,182));controls.addView(title);
        LinearLayout buttons=new LinearLayout(this);controls.addView(buttons);
        button(buttons,"Actualizar",()->loadCatalog(true,null));
        button(buttons,"★ Favorito",()->{if(current==null)return;if(!favorites.add(current.key()))favorites.remove(current.key());prefs.edit().putStringSet("favorites",favorites).apply();filter();});
        button(buttons,"Pantalla",this::toggleFullscreen);
        groups=new Spinner(this);controls.addView(groups);
        search=new EditText(this);search.setSingleLine(true);search.setHint("Buscar canal");search.setTextColor(Color.WHITE);search.setHintTextColor(Color.LTGRAY);controls.addView(search);
        now=new TextView(this);now.setText("Elegí un canal");now.setTextColor(Color.WHITE);root.addView(now);
        video=new PlayerView(this);video.setKeepScreenOn(true);video.setUseController(true);root.addView(video,new LinearLayout.LayoutParams(-1,dp(210)));
        status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(12);root.addView(status);
        browser=new LinearLayout(this);browser.setOrientation(1);root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        list=new ListView(this);adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());list.setAdapter(adapter);browser.addView(list,new LinearLayout.LayoutParams(-1,-1));
        list.setOnItemClickListener((parent,v,pos,id)->play(shown.get(pos)));
        list.setOnItemLongClickListener((parent,v,pos,id)->{Channel c=shown.get(pos);if(!favorites.add(c.key()))favorites.remove(c.key());prefs.edit().putStringSet("favorites",favorites).apply();filter();return true;});
        groups.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){filter();} public void onNothingSelected(AdapterView<?> p){}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){filter();}public void afterTextChanged(Editable e){}});
        loadCatalog(false,null);
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value);}
    private void button(LinearLayout row,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->action.run());}
    private String download(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","MonchoPlayer/1.0");
        try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>8*1024*1024)throw new IOException("Catálogo demasiado grande");}return out.toString("UTF-8");}}finally{c.disconnect();}
    }
    private void parse(String body,ArrayList<Channel> result,Set<String> seen)throws Exception{
        JSONArray categories=new JSONObject(body).getJSONArray("categories");
        for(int i=0;i<categories.length();i++){JSONObject cat=categories.getJSONObject(i);JSONArray entries=cat.optJSONArray("samples");if(entries==null)continue;
            for(int j=0;j<entries.length();j++){JSONObject item=entries.getJSONObject(j);Channel c=new Channel();c.name=item.optString("name","Sin nombre");c.group=cat.optString("name","Otros");c.url=item.optString("original_url","").trim();c.type=item.optString("type","");c.drm=c.type.toUpperCase(Locale.ROOT).matches(".*(CLEARKEY|WIDEVINE|PLAYREADY|DRM).*")||!item.optString("drm_license_uri","").isEmpty()&&!item.isNull("drm_license_uri");
                JSONObject h=item.optJSONObject("headers");if(h!=null)for(String name:new String[]{"User-Agent","Origin","Referer"}){String value=h.optString(name,"");if(!value.isEmpty()&&!value.contains("\n")&&!value.contains("\r"))c.headers.put(name,value);}
                // No se usan claves o licencias incrustadas para configurar la reproducción.
                if(seen.add(c.name+"\n"+c.url+"\n"+c.type+"\n"+new TreeMap<>(c.headers)))result.add(c);
            }
        }
    }
    private void loadCatalog(boolean force,Channel retry){
        if(loading)return;loading=true;status.setText("Consultando canales…");
        io.execute(()->{try{
            ArrayList<Channel> result=new ArrayList<>();Set<String> seen=new HashSet<>();boolean cached=false;
            for(String source:new String[]{"channels_alt.json","original_url.json"}){
                String body;File cache=new File(getFilesDir(),source);
                try{body=download(BASE+source);new JSONObject(body).getJSONArray("categories");try(FileOutputStream out=new FileOutputStream(cache)){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                catch(Exception error){if(force||!cache.exists())throw error;try(FileInputStream in=new FileInputStream(cache);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);body=out.toString("UTF-8");cached=true;}}
                parse(body,result,seen);
            }
            if(result.isEmpty())throw new IOException("Catálogo vacío");final boolean offline=cached;
            runOnUiThread(()->{if(destroyed)return;loading=false;channels.clear();channels.addAll(result);String selected=groups.getSelectedItem()==null?"Todos":groups.getSelectedItem().toString();LinkedHashSet<String> names=new LinkedHashSet<>();names.add("Todos");names.add("Favoritos");for(Channel c:channels)names.add(c.group);ArrayList<String> options=new ArrayList<>(names);groups.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));groups.setSelection(Math.max(0,options.indexOf(selected)));filter();status.setText(channels.size()+" canales · "+(offline?"copia guardada; sin actualización completa":"catálogo actualizado"));
                if(retry!=null&&current==retry&&!stopped){for(Channel c:channels)if(c.key().equals(retry.key())&&!c.url.equals(retry.url)){play(c,false);return;}status.setText("El catálogo no tiene un enlace nuevo para este canal. Puede requerir acceso del proveedor.");}
            });
        }catch(Exception error){runOnUiThread(()->{if(destroyed)return;loading=false;status.setText("No se pudo actualizar. Revisá la conexión y volvé a intentar.");});}});
    }
    private void filter(){if(adapter==null)return;String q=search.getText().toString().toLowerCase(Locale.ROOT);String group=groups.getSelectedItem()==null?"Todos":groups.getSelectedItem().toString();shown.clear();ArrayList<String> labels=new ArrayList<>();for(Channel c:channels){if(!c.name.toLowerCase(Locale.ROOT).contains(q))continue;if(group.equals("Favoritos")&&!favorites.contains(c.key()))continue;if(!group.equals("Todos")&&!group.equals("Favoritos")&&!group.equals(c.group))continue;shown.add(c);labels.add((favorites.contains(c.key())?"★ ":"")+c.label());}adapter.clear();adapter.addAll(labels);adapter.notifyDataSetChanged();}
    private void play(Channel c){play(c,true);}
    private void play(Channel c,boolean allowRefresh){
        generation++;final int request=generation;current=c;releasePlayer();now.setText(c.name);
        if(!c.complete()){status.setText("URL incompleta en la fuente. No hay servidor base configurado.");return;}
        if(c.type.equals("WEBVIEW")){status.setText("Este registro es una página web, no una señal directa.");return;}
        if(c.drm){status.setText("Canal con DRM: requiere integración y licencia autorizada del proveedor.");return;}
        if(stopped)return;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(false).setDefaultRequestProperties(c.headers);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http))).build();video.setPlayer(player);
        player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException error){if(request!=generation)return;status.setText("No se pudo reproducir: "+error.getErrorCodeName());Throwable cause=error;while(cause!=null){if(cause instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;if(allowRefresh&&(code==401||code==403)){status.setText("Revisando si el catálogo publicó un enlace nuevo…");loadCatalog(true,c);}break;}cause=cause.getCause();}}@Override public void onPlaybackStateChanged(int state){if(request==generation&&state==Player.STATE_READY)status.setText("Reproduciendo · "+c.name);}});
        MediaItem.Builder item=new MediaItem.Builder().setUri(Uri.parse(c.url));String path=Uri.parse(c.url).getPath();if(path!=null&&path.endsWith(".mpd"))item.setMimeType(MimeTypes.APPLICATION_MPD);else if(c.type.equalsIgnoreCase("HLS"))item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build());player.prepare();player.play();status.setText("Conectando…");
    }
    private void toggleFullscreen(){fullscreen=!fullscreen;controls.setVisibility(fullscreen?View.GONE:View.VISIBLE);browser.setVisibility(fullscreen?View.GONE:View.VISIBLE);video.setLayoutParams(new LinearLayout.LayoutParams(-1,fullscreen?0:dp(210),fullscreen?1:0));}
    private void releasePlayer(){if(player!=null){video.setPlayer(null);player.release();player=null;}}
    @Override public void onBackPressed(){if(fullscreen)toggleFullscreen();else super.onBackPressed();}
    @Override protected void onStop(){stopped=true;generation++;releasePlayer();super.onStop();}
    @Override protected void onStart(){super.onStart();stopped=false;}
    @Override protected void onDestroy(){destroyed=true;generation++;releasePlayer();io.shutdownNow();super.onDestroy();}
}
