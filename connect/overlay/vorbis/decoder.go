package vorbis

// Pure-Go dekodér Ogg Vorbis (github.com/jfreymuth/oggvorbis) místo libvorbis přes cgo –
// aby šel go-librespot zkompilovat staticky bez C toolchainu (Hodiny / Android armv7).

import (
	"errors"
	"io"
	"sync"

	librespot "github.com/devgianlu/go-librespot"
	"github.com/jfreymuth/oggvorbis"
)

type Decoder struct {
	sync.Mutex

	log librespot.Logger

	SampleRate int32
	Channels   int32

	meta  *MetadataPage
	gain  float32
	input librespot.SizedReadAtSeeker
	r     *oggvorbis.Reader

	closed bool
}

func New(log librespot.Logger, r librespot.SizedReadAtSeeker, meta *MetadataPage, gain float32) (*Decoder, error) {
	rd, err := oggvorbis.NewReader(r)
	if err != nil {
		return nil, err
	}
	return &Decoder{
		log:        log,
		input:      r,
		meta:       meta,
		gain:       gain,
		r:          rd,
		SampleRate: int32(rd.SampleRate()),
		Channels:   int32(rd.Channels()),
	}, nil
}

func (d *Decoder) Close() {
	d.Lock()
	defer d.Unlock()
	d.closed = true
}

func (d *Decoder) Read(p []float32) (n int, err error) {
	d.Lock()
	defer d.Unlock()
	if d.closed {
		return 0, errors.New("decoder: decoder has already been closed")
	}
	for n < len(p) {
		var m int
		m, err = d.r.Read(p[n:])
		n += m
		if err != nil {
			break
		}
		if m == 0 {
			err = io.EOF
			break
		}
	}
	if d.gain != 1 {
		for i := 0; i < n; i++ {
			p[i] *= d.gain
		}
	}
	return n, err
}

func (d *Decoder) SetPositionMs(pos int64) error {
	d.Lock()
	defer d.Unlock()
	samples := pos * int64(d.SampleRate) / 1000
	if l := d.r.Length(); l > 0 && samples >= l {
		samples = l - 1
	}
	if samples < 0 {
		samples = 0
	}
	return d.r.SetPosition(samples)
}

func (d *Decoder) PositionMs() int64 {
	if d.SampleRate == 0 {
		return 0
	}
	return d.r.Position() * 1000 / int64(d.SampleRate)
}
