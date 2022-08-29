# ZWORDS

ZWords is a Wordle (motus) like game using [scala][scala], [ZIO][zio],
[tapir][tapir], [zhttp][zhttp] for the backend. 
**multiple dictionaries supported, the deployed release comes with english and french dictionaries**.

- **Play it** : https://zwords.mapland.fr/ 
- Code your AI Bot with this API : https://zwords.mapland.fr/docs/
- User interface by [briossantC][bri] - [@briossant][tbri] using [threejs][three]

![](images/screen.jpg)

# Notes

## Requirements

When LVMDB is used for as persistence store with recent JVM it requires JVM some options :
```
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
```

[scala]: https://www.scala-lang.org/
[zio]: https://zio.dev/
[tapir]: https://tapir.softwaremill.com/
[zhttp]: https://github.com/dream11/zio-http
[bri]: https://github.com/briossant
[tbri]: https://twitter.com/BriossantC
[three]: https://threejs.org/
