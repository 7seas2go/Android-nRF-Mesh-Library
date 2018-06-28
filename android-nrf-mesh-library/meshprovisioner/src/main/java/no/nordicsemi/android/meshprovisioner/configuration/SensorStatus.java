/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.meshprovisioner.configuration;

import android.content.Context;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import no.nordicsemi.android.meshprovisioner.InternalTransportCallbacks;
import no.nordicsemi.android.meshprovisioner.MeshConfigurationStatusCallbacks;
import no.nordicsemi.android.meshprovisioner.messages.AccessMessage;
import no.nordicsemi.android.meshprovisioner.messages.ControlMessage;
import no.nordicsemi.android.meshprovisioner.messages.Message;
import no.nordicsemi.android.meshprovisioner.opcodes.ApplicationMessageOpCodes;
import no.nordicsemi.android.meshprovisioner.transport.UpperTransportLayerCallbacks;
import no.nordicsemi.android.meshprovisioner.utils.MeshParserUtils;

public final class SensorStatus extends ConfigMessage implements UpperTransportLayerCallbacks{

    private static final String TAG = SensorStatus.class.getSimpleName();

    public SensorStatus(Context context,
                        final ProvisionedMeshNode unprovisionedMeshNode,
                        final MeshModel meshModel,
                        final int appKeyIndex,
                        final InternalTransportCallbacks internalTransportCallbacks,
                        final MeshConfigurationStatusCallbacks meshConfigurationStatusCallbacks) {
        super(context, unprovisionedMeshNode);
        this.mMeshModel = meshModel;
        this.mAppKeyIndex = appKeyIndex;
        this.mInternalTransportCallbacks = internalTransportCallbacks;
        this.mConfigStatusCallbacks = meshConfigurationStatusCallbacks;
        this.mMeshTransport.setUpperTransportLayerCallbacks(this);
    }

    @Override
    public MessageState getState() {
        return MessageState.SENSOR_STATUS;
    }

    public void parseData(final byte[] pdu) {
        parseMessage(pdu);
    }

    private void parseMessage(final byte[] pdu) {
        final Message message = mMeshTransport.parsePdu(mSrc, pdu);
        if (message != null) {
            if (message instanceof AccessMessage) {
                final AccessMessage accessMessage = (AccessMessage) message;
//                final byte[] accessPayload = accessMessage.getAccessPdu();
//                final int opCodeLength = ((accessPayload[0] >> 7) & 0x01) + 1;
                ArrayList<Byte> sensorData = new ArrayList<>();

                final short opcode = (short)accessMessage.getOpCode();

                if (opcode != ApplicationMessageOpCodes.SENSOR_STATUS) {
                    mConfigStatusCallbacks.onUnknownPduReceived(mProvisionedMeshNode);
                    return;
                }

                Log.v(TAG, "Received sensor status");
                final ByteBuffer buffer = ByteBuffer.wrap(accessMessage.getParameters()).order(ByteOrder.LITTLE_ENDIAN);

                mConfigStatusCallbacks.onSensorStatusReceived(mProvisionedMeshNode, sensorData);
                mInternalTransportCallbacks.updateMeshNode(mProvisionedMeshNode);
            } else {
                parseControlMessage((ControlMessage) message);
            }
        } else {
            Log.v(TAG, "Message reassembly may not be complete yet");
        }
    }

    @Override
    public void sendSegmentAcknowledgementMessage(final ControlMessage controlMessage) {
        final ControlMessage message = mMeshTransport.createSegmentBlockAcknowledgementMessage(controlMessage);
        Log.v(TAG, "Sending acknowledgement: " + MeshParserUtils.bytesToHex(message.getNetworkPdu().get(0), false));
        mInternalTransportCallbacks.sendPdu(mProvisionedMeshNode, message.getNetworkPdu().get(0));
        mConfigStatusCallbacks.onBlockAcknowledgementSent(mProvisionedMeshNode);
    }

    @Override
    public byte[] getApplicationKey() {
        if(mMeshModel != null){
            if(!mMeshModel.getBoundAppkeys().isEmpty()){
                if(mAppKeyIndex >= 0) {
                    return MeshParserUtils.toByteArray(mMeshModel.getBoundAppKey(mAppKeyIndex));
                }
            }
        }
        return null;
    }
}
