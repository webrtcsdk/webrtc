/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef TEST_MOCK_AUDIO_DECODER_FACTORY_H_
#define TEST_MOCK_AUDIO_DECODER_FACTORY_H_

#include <memory>
#include <vector>

#include "api/audio_codecs/audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/environment/environment.h"
#include "api/make_ref_counted.h"
#include "api/scoped_refptr.h"
#include "test/gmock.h"

namespace webrtc {

class MockAudioDecoderFactory : public AudioDecoderFactory {
 public:
  // Creates a MockAudioDecoderFactory with no formats and that may not be
  // invoked to create a codec - useful for initializing a voice engine, for
  // example.
  static scoped_refptr<AudioDecoderFactory> CreateUnusedFactory() {
    auto factory =
        make_ref_counted<testing::NiceMock<MockAudioDecoderFactory>>();
    EXPECT_CALL(*factory, Create).Times(0);
    return factory;
  }

  // Creates a MockAudioDecoderFactory with no formats that may be invoked to
  // create a codec any number of times. It will, though, return nullptr on each
  // call, since it supports no codecs.
  static scoped_refptr<AudioDecoderFactory> CreateEmptyFactory() {
    return make_ref_counted<testing::NiceMock<MockAudioDecoderFactory>>();
  }

  MOCK_METHOD(std::vector<AudioCodecSpec>,
              GetSupportedDecoders,
              (),
              (override));
  MOCK_METHOD(bool, IsSupportedDecoder, (const SdpAudioFormat&), (override));
  MOCK_METHOD(std::unique_ptr<AudioDecoder>,
              Create,
              (const Environment&,
               const SdpAudioFormat&,
               absl::optional<AudioCodecPairId>),
              (override));
};

}  // namespace webrtc

#endif  // TEST_MOCK_AUDIO_DECODER_FACTORY_H_
