import 'package:flutter/material.dart';
import 'package:audio_notification/audio_notification.dart';

void main() => runApp(new MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String status = 'stopped';

  List<AudioNotificationPlaylist> playlists = [
    AudioNotificationPlaylist(songs: [
      {
        "id": "1",
        "title": "Op1",
        "author": "Durarara",
        "image": "https://utakashi.com/media/img/animes/5d234d4dd8221.png",
        "source": "https://utakashi.com/media/songs/5a7f534bba088.mp3",
      },
    ], metadata: {
      "id": "1",
      "title": "Playlist one"
    }),
  ];

  @override
  void initState() {
    super.initState();

    AudioNotification.init().then((e) {
      AudioNotification.setCustomOption("test", "test_value2");
      AudioNotification.getCustomOption("test");
      setState(() {});

      if (AudioNotification.playlist != null) {
        if (AudioNotification.playing) {
          setState(() => status = 'play');
        } else {
          setState(() => status = 'pause');
        }
      }
    });

    AudioNotification.onTogglePlayback((bool playing) {
      setState(() => status = playing ? 'play' : 'pause');
    });

    AudioNotification.onDuration((int duration) {
      setState(() {});
    });

    AudioNotification.onPosition((int position) {
      setState(() {});
    });

    AudioNotification.onNext(() {
      setState(() {});
    });

    AudioNotification.onPrev(() {
      setState(() {});
    });

    AudioNotification.onSelect(() {
      print('notification selected');
    });

    AudioNotification.onStop(() {
      setState(() => status = 'stopped');
    });
  }

  stop() {
    AudioNotification.stop();
  }

  toggle() {
    AudioNotification.toggle();
  }

  toggleRepeat() async {
    await AudioNotification.toggleRepeat();
    setState(() {});
  }

  toggleShuffle() async {
    await AudioNotification.toggleShuffle();
    setState(() {});
  }

  play(AudioNotificationPlaylist playlist, int index) async {
    await AudioNotification.setPlaylist(playlist);
    AudioNotification.play(index);
  }

  Widget _buildPlaylist(AudioNotificationPlaylist playlist) {
    return Flexible(
        flex: 2,
        child: Padding(
          padding: EdgeInsets.only(top: 20.0),
          child: Column(
            children: <Widget>[
              Text(
                playlist.metadata["title"],
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0),
              ),
              ListView.builder(
                shrinkWrap: true,
                itemBuilder: (context, i) => ListTile(
                  title: Text(
                    playlist.songs[i]["title"],
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 14.0),
                  ),
                  onTap: () => play(playlist, i),
                ),
                itemCount: playlist.songs.length,
              )
            ],
          ),
        ));
  }

  @override
  Widget build(BuildContext context) {
    var player = AudioNotification.song == null
        ? Container()
        : Container(
            height: 240.0,
            child: SingleChildScrollView(
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: <Widget>[
                      FlatButton(child: Icon(Icons.close), onPressed: stop),
                    ],
                  ),
                  Divider(),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      AudioNotification.song == null
                          ? Container()
                          : Flexible(
                              child: Column(
                                children: <Widget>[
                                  Text(
                                    AudioNotification.song["title"],
                                    style: TextStyle(
                                      fontSize: 16.0,
                                    ),
                                  ),
                                  Text(AudioNotification.song["author"]),
                                  SizedBox(height: 12.0),
                                  Text(
                                    (AudioNotification
                                            .playlist.metadata["title"] ??
                                        ""),
                                    style: TextStyle(
                                      fontSize: 16.0,
                                      color: Colors.grey,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                    ],
                  ),
                  Text('${AudioNotification.position}/${AudioNotification.duration}'),
                  Slider(
                    onChanged: (val) {
                      AudioNotification.seekTo(val.toInt());
                    },
                    value: AudioNotification.position.toDouble(),
                    max: AudioNotification.duration.toDouble(),
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      Container(
                        child: FlatButton(
                          padding: EdgeInsets.all(4.0),
                          child: Icon(
                            Icons.repeat,
                            size: 24.0,
                            color: AudioNotification.repeat
                                ? Colors.blue
                                : Colors.black,
                          ),
                          onPressed: toggleRepeat,
                        ),
                        width: 40.0,
                      ),
                      Container(
                        child: FlatButton(
                          padding: EdgeInsets.all(4.0),
                          child: Icon(Icons.skip_previous, size: 30.0),
                          onPressed: AudioNotification.prev,
                        ),
                        width: 50.0,
                      ),
                      Container(
                        child: FlatButton(
                          padding: EdgeInsets.all(4.0),
                          child: Icon(
                            status == 'pause'
                                ? Icons.play_circle_outline
                                : Icons.pause_circle_outline,
                            size: 40.0,
                          ),
                          onPressed: toggle,
                        ),
                        width: 60.0,
                      ),
                      Container(
                        child: FlatButton(
                          padding: EdgeInsets.all(4.0),
                          child: Icon(Icons.skip_next, size: 30.0),
                          onPressed: AudioNotification.next,
                        ),
                        width: 50.0,
                      ),
                      Container(
                        child: FlatButton(
                          padding: EdgeInsets.all(4.0),
                          child: Icon(
                            Icons.shuffle,
                            size: 24.0,
                            color: AudioNotification.shuffle
                                ? Colors.blue
                                : Colors.black,
                          ),
                          onPressed: toggleShuffle,
                        ),
                        width: 40.0,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );

    return MaterialApp(
      home: Scaffold(
          appBar: AppBar(
            title: Text('Plugin example app'),
          ),
          body: Column(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: <Widget>[
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: <Widget>[
                  _buildPlaylist(playlists[0]),
                ],
              ),
              status == 'stopped' ? Container() : player,
            ],
          )),
    );
  }
}
