# Third-party license sources

LimitForge's original MIT license and copyright notice are in the repository
root `LICENSE`. Runtime dependencies have their own licenses. The shaded JAR
preserves and merges embedded `META-INF/LICENSE`, `META-INF/LICENSE.txt`,
`META-INF/NOTICE`, and `META-INF/NOTICE.txt` resources supplied by dependencies.

Supplementary upstream license copies below are included in both
the runnable distribution's `licenses/` directory and the executable JAR at
`META-INF/limitforge/third-party/`.

| Dependency | Included license files | Pinned upstream source |
| --- | --- | --- |
| `org.quickfixj:quickfixj-core:2.3.1`, `org.quickfixj:quickfixj-messages-all:2.3.1` | [QuickFIX Software License 1.0](quickfixj-2.3.1/LICENSE) | [LICENSE at `f72feb612c7fd5d519c916874dbd00121eb71298`](https://github.com/quickfix-j/quickfixj/blob/f72feb612c7fd5d519c916874dbd00121eb71298/LICENSE), tag `QFJ_RELEASE_2_3_1` |
| `ch.qos.logback:logback-core:1.6.5`, `ch.qos.logback:logback-classic:1.6.5` | [Upstream copyright and license choice](logback-1.6.5/LICENSE.txt), [EPL 2.0 full text](logback-1.6.5/EPL-2.0.html) | [LICENSE.txt at `d1b829dcdb9fd98511c64401beb1419a9c9384aa`](https://github.com/qos-ch/logback/blob/d1b829dcdb9fd98511c64401beb1419a9c9384aa/LICENSE.txt), tag `v_1.6.5` |

The QuickFIX/J license file retains upstream Git blob ID
`7e5349963b323e8b1f619b5c61e22d9f15d8c24c`. The Logback license-choice file
retains upstream Git blob ID `55db596024ff8936b274fd0e12197dd116af0472`.
The full EPL 2.0 HTML is copied from the
[Eclipse Foundation's official text](https://www.eclipse.org/legal/epl-v20.html)
retrieved on 2026-10-22. License wording and copyright notices are unmodified.

Logback's upstream license explicitly allows the licensee to choose either
EPL 2.0 or LGPL 2.1. This distribution uses the EPL 2.0 option for the bundled
Logback components. Their source is available under EPL 2.0 from the
[pinned Logback 1.6.5 source tree](https://github.com/qos-ch/logback/tree/d1b829dcdb9fd98511c64401beb1419a9c9384aa).
The bundled Logback classes have not been modified. For QuickFIX/J, see the
[pinned 2.3.1 source tree](https://github.com/quickfix-j/quickfixj/tree/f72feb612c7fd5d519c916874dbd00121eb71298).

Required QuickFIX acknowledgment:

> This product includes software developed by
> quickfixengine.org (http://www.quickfixengine.org/).

When dependency versions change, recheck the upstream license and notice files
and update these pinned links alongside the copies. These supplementary files
do not replace any additional notices embedded in the executable JAR.
