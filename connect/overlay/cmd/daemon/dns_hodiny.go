package main

// Hodiny (Android bez rootu): statická linuxová binárka nemá /etc/resolv.conf,
// proto DNS server předává aplikace v proměnné HODINY_DNS (čárkami oddělené IP).

import (
	"context"
	"net"
	"os"
	"strings"
	"time"
)

func init() {
	// Android: kořenové certifikáty nejsou v /etc/ssl (statická linuxová binárka je jinak nenajde)
	if os.Getenv("SSL_CERT_DIR") == "" {
		if _, err := os.Stat("/system/etc/security/cacerts"); err == nil {
			os.Setenv("SSL_CERT_DIR", "/system/etc/security/cacerts:/apex/com.android.conscrypt/cacerts")
		}
	}
	servers := []string{}
	for _, s := range strings.Split(os.Getenv("HODINY_DNS"), ",") {
		if s = strings.TrimSpace(s); s != "" {
			servers = append(servers, net.JoinHostPort(s, "53"))
		}
	}
	if _, err := os.Stat("/etc/resolv.conf"); err == nil && len(servers) == 0 {
		return
	}
	servers = append(servers, "1.1.1.1:53", "8.8.8.8:53")
	net.DefaultResolver = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			d := net.Dialer{Timeout: 3 * time.Second}
			var last error
			for _, s := range servers {
				c, err := d.DialContext(ctx, network, s)
				if err == nil {
					return c, nil
				}
				last = err
			}
			return nil, last
		},
	}
}
