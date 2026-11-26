//go:build !cgo

package mp3

import (
	"errors"

	librespot "github.com/devgianlu/go-librespot"
)

// Bez cgo (statické sestavení pro Hodiny) tento formát není k dispozici; přehrává se Ogg Vorbis.
type Decoder struct {
	SampleRate int32
	Channels   int32
	BitDepth   int32
}

func New(log librespot.Logger, r librespot.SizedReadAtSeeker, gain float32) (*Decoder, error) {
	return nil, errors.New("mp3: not available in this build")
}

func (d *Decoder) Read(p []float32) (int, error) { return 0, errors.New("mp3: not available") }
func (d *Decoder) SetPositionMs(pos int64) error  { return errors.New("mp3: not available") }
func (d *Decoder) PositionMs() int64              { return 0 }
func (d *Decoder) Close() error                   { return nil }
