/*
 *  Copyright (c) 2013 The CCP project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a Beijing Speedtong Information Technology Co.,Ltd license
 *  that can be found in the LICENSE file in the root of the web site.
 *
 *   http://www.cloopen.com
 *
 *  An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package com.speedtong.example.meeting.ui.interphonemeeting;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.screen.main.R;
import com.speedtong.example.meeting.ECApplication;
import com.speedtong.example.meeting.common.utils.CCPUtil;
import com.speedtong.example.meeting.core.CCPConfig;
import com.speedtong.example.meeting.core.SDKCoreHelper;
import com.speedtong.example.meeting.ui.CCPBaseActivity;
import com.speedtong.example.meeting.ui.manager.CCPAppManager;
import com.speedtong.sdk.ECDevice;
import com.speedtong.sdk.ECError;
import com.speedtong.sdk.core.ECGlobalConstants;
import com.speedtong.sdk.core.ECMeetingType;
import com.speedtong.sdk.core.interphone.ECInterphoneControlMicMsg;
import com.speedtong.sdk.core.interphone.ECInterphoneExitMsg;
import com.speedtong.sdk.core.interphone.ECInterphoneInviteMsg;
import com.speedtong.sdk.core.interphone.ECInterphoneJoinMsg;
import com.speedtong.sdk.core.interphone.ECInterphoneMeetingMember;
import com.speedtong.sdk.core.interphone.ECInterphoneMeetingMsg;
import com.speedtong.sdk.core.interphone.ECInterphoneOverMsg;
import com.speedtong.sdk.core.interphone.ECInterphoneReleaseMicMsg;
import com.speedtong.sdk.core.meeting.interphone.listener.OnControlMicInInterphoneListener;
import com.speedtong.sdk.core.meeting.interphone.listener.OnCreateInterphoneMeetingListener;
import com.speedtong.sdk.core.meeting.interphone.listener.OnReleaseMicInInterphoneListener;
import com.speedtong.sdk.core.meeting.listener.OnCreateOrJoinMeetingListener;
import com.speedtong.sdk.core.meeting.listener.OnQueryMeetingMembersListener;
import com.speedtong.sdk.core.videomeeting.ECVideoMeetingMember;
import com.speedtong.sdk.core.voicemeeting.ECVoiceMeetingMember;
import com.speedtong.sdk.debug.ECLog4Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InterPhoneRoomActivity extends CCPBaseActivity implements
		View.OnTouchListener {

	private static final long INTER_PHONE_TIME_INTERVAL = 500;

	private static final String TYPE_UNONLINE = "0";
	private static final String TYPE_ONLINE = "1";

	private static final String TYPE_MIC_CONTROLER = "1";
	private static final String TYPE_MIC_UNCONTROL = "0";

	private static final String TYPE_SPEAK_INITIATOR = "1";
	private static final String TYPE_SPEAK_UNINITIATOR = "0";

	public static final int MESSAGE_TYPE_JOIN = 101;
	public static final int MESSAGE_TYPE_EXIT = 102;
	public static final int MESSAGE_TYPE_CONTROL_MIC = 103;
	public static final int MESSAGE_TYPE_RELEASE_MIC = 104;

	protected static final String TAG = "InterPhoneRoomActivity";

	private ListView mInterphoneList;
	private TextView mNoticeTips;
	private Button mInterSpeak;

	private InterPhoneMemberAdapter mIPoneMemAdapter;

	private ArrayList<ECInterphoneMeetingMember> mJoinMembers;

	private Chronometer mChronometer;
	private TextView mPersonCount;
	private int onLineNum;

	AudioManager mAudioManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
						| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		handleTitleDisplay(null, null, getString(R.string.str_quit));

		mInterphoneList = (ListView) findViewById(R.id.interphone_list);
		mNoticeTips = (TextView) findViewById(R.id.notice_tips);
		mInterSpeak = (Button) findViewById(R.id.interphone_speak);

		mInterSpeak.setEnabled(false);
		mInterSpeak.setOnTouchListener(this);

		mChronometer = (Chronometer) findViewById(R.id.chronometer);
		mPersonCount = (TextView) findViewById(R.id.count_tv);

		SDKCoreHelper.getInstance().setHandler(mHandler);
		initialize(savedInstanceState);

		CCPConfig.VoIP_ID = CCPAppManager.getClientUser().getUserId();
	}

	private void InitIntercom(ArrayList<String> mInviterMember) {
		// Initiated intercom ...
		mJoinMembers = new ArrayList<ECInterphoneMeetingMember>();
		mInviterMember.add(CCPConfig.VoIP_ID);
		for (String member : mInviterMember) {
			ECInterphoneMeetingMember interMember = new ECInterphoneMeetingMember();
			interMember.mic = TYPE_MIC_UNCONTROL;
			interMember.online = TYPE_UNONLINE;
			interMember.voipId = member;
			if (CCPConfig.VoIP_ID.equals(member)) {
				interMember.type = TYPE_SPEAK_INITIATOR;
				mJoinMembers.add(0, interMember);
			} else {
				interMember.type = TYPE_SPEAK_UNINITIATOR;
				mJoinMembers.add(interMember);
			}
		}
		mPersonCount.setText("0/" + mJoinMembers.size());
		mInviterMember.remove(CCPConfig.VoIP_ID);

		ECDevice.getECMeetingManager().createInterphoneMeeting(
				CCPConfig.App_ID, mInviterMember.toArray(new String[] {}),
				new OnCreateInterphoneMeetingListener() {

					@Override
					public void OnCreateInterphoneMeeting(ECError reason,
							String meetingNo) {

						confNo = meetingNo;
						ECLog4Util.i(TAG, reason.toString() + ",meetingNo="
								+ meetingNo);

						if (reason.errorCode.equals(SDKCoreHelper.NO_ERROR)) {

							ECDevice.getECMeetingManager().joinMeetingByType(
									meetingNo,
									ECMeetingType.ECMeetingInterPhone, "",
									new OnCreateOrJoinMeetingListener() {

										@Override
										public void onCreateOrJoinMeeting(
												ECError reason, String meetingNo) {
											ECLog4Util.i(TAG, reason.toString()
													+ ",meetingNo=" + meetingNo);

											if (reason.errorCode
													.equals(SDKCoreHelper.NO_ERROR)) {
												confNo = meetingNo;
												ECDevice.getECVoipSetManager()
														.enableLoudSpeaker(true);

												doQueryMembersInInterphone();
											}

										}

									});
						}
					}
				});

		mNoticeTips.setText(getString(R.string.str_top_notice_tips_invite,
				mInviterMember.size()));
		mIPoneMemAdapter = new InterPhoneMemberAdapter(getApplicationContext(),
				mJoinMembers);
		mInterphoneList.setAdapter(mIPoneMemAdapter);
	}

	private void doQueryMembersInInterphone() {

		ECDevice.getECVoipSetManager().enableLoudSpeaker(true);

		ECDevice.getECMeetingManager().queryMeetingMembersByType(confNo,
				ECMeetingType.ECMeetingInterPhone,
				new OnQueryMeetingMembersListener() {

					@Override
					public void onQueryVoiceMeetingMembers(ECError reason,
							List<ECVoiceMeetingMember> members) {

					}

					@Override
					public void onQueryVideoMeetingMembers(ECError reason,
							List<ECVideoMeetingMember> members) {

					}

					@Override
					public void onQueryInterphoneMeetingMembers(ECError reason,
							List<ECInterphoneMeetingMember> members) {

						mInterSpeak.setEnabled(true);
						if (reason.errorCode.equals("111609")) {
							ECDevice.getECMeetingManager().exitMeeting();
							finish();
							return;
						}
						if (members != null) {
							if (mJoinMembers == null) {
								mJoinMembers = new ArrayList<ECInterphoneMeetingMember>();
							}
							mJoinMembers.clear();
							for (ECInterphoneMeetingMember i : members) {
								if (i.voipId.equals(CCPAppManager
										.getClientUser().getUserId())) {
									mJoinMembers.add(0, i);
								} else {
									mJoinMembers.add(i);
									// 语音对讲由于 创建对讲时候 创建并进入对讲 发了一次sip
									// 和查询对讲成员会重复统计成员个数。
									// 查询成员时候排除自己
									if (TYPE_ONLINE.equals(i.online)) {
										if (onLineNum < members.size())
											increase(1);
									}
								}
							}
							mPersonCount.setText(onLineNum + "/"
									+ members.size());
							mIPoneMemAdapter = new InterPhoneMemberAdapter(
									getApplicationContext(), mJoinMembers);
							mInterphoneList.setAdapter(mIPoneMemAdapter);
						}

					}
				});
		mNoticeTips.setText(CCPAppManager.getClientUser().getUserId()
				+ getString(R.string.str_join_inter_phone_success));
		setActivityTitle(getString(R.string.str_inter_phone_room_title,
				confNo.substring(confNo.length() - 3, confNo.length())));

	}

	private void initialize(Bundle savedInstanceState) {
		// read parameters or previously saved state of this activity.
		Intent intent = getIntent();
		if (intent.hasExtra("confNo")) {
			// join to invite an intercom
			Bundle extras = intent.getExtras();
			if (extras != null) {
				String confNo = extras.getString("confNo");
				this.confNo = confNo;
				try {
					ECDevice.getECMeetingManager().joinMeetingByType(confNo,
							ECMeetingType.ECMeetingInterPhone, "",
							new OnCreateOrJoinMeetingListener() {

								@Override
								public void onCreateOrJoinMeeting(
										ECError reason, String meetingNo) {
									ECLog4Util.i(TAG, reason.toString()
											+ ",meetingNo=" + meetingNo);

									if (reason.errorCode
											.equals(SDKCoreHelper.NO_ERROR)) {

										ECDevice.getECVoipSetManager()
												.enableLoudSpeaker(true);

										doQueryMembersInInterphone();
									}

								}

							});

				} catch (Exception e) {
					Log.e("InterPhoneRoomActivity",
							"getDeviceHelper().joinInterphone Exception");
				}

				mNoticeTips.setText(R.string.top_tips_connecting_wait);
				setActivityTitle(getString(R.string.str_inter_phone_room_title,
						confNo.substring(confNo.length() - 3, confNo.length())));
			}

		} else if (intent.hasExtra("InviterMember")) {
			Bundle extras = intent.getExtras();
			ArrayList<String> mInviterMember = null;
			if (extras != null) {
				mInviterMember = extras.getStringArrayList("InviterMember");

			}
			if (mInviterMember == null || mInviterMember.isEmpty()) {
				throw new IllegalStateException(
						"Invalid inviter phone  member error :" + " "
								+ mInviterMember);
			} else {
				//
			}

			InitIntercom(mInviterMember);
		}

	}

	boolean isDownEvent;
	long downTime = 0;

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		Message obtainMessage = null;
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			lockScreen();
			if (mHandler != null) {
				obtainMessage = mHandler
						.obtainMessage(SDKCoreHelper.WHAT_ON_PLAY_MUSIC);
				mHandler.sendMessage(obtainMessage);
			}
			isDownEvent = true;
			downTime = event.getDownTime();
			mInterSpeak
					.setBackgroundResource(R.drawable.voice_intephone_pressed);
			if (mHandler != null) {
				obtainMessage = mHandler
						.obtainMessage(SDKCoreHelper.WHAT_ON_REQUEST_MIC_CONTROL);
				mHandler.sendMessageDelayed(obtainMessage,
						INTER_PHONE_TIME_INTERVAL);
			}
			break;
		case MotionEvent.ACTION_UP:
			// mChronometer.stop();
			// mChronometer.setVisibility(View.INVISIBLE);
			releaseLockScreen();
			isDownEvent = false;
			if (mHandler != null) {
				mHandler.removeMessages(SDKCoreHelper.WHAT_ON_REQUEST_MIC_CONTROL);
			}
			mInterSpeak
					.setBackgroundResource(R.drawable.voice_intephone_normal);
			if ((event.getEventTime() - downTime) >= INTER_PHONE_TIME_INTERVAL) {

				ECDevice.getECMeetingManager().releaseMicInInterphoneMeeting(
						confNo, new OnReleaseMicInInterphoneListener() {

							@Override
							public void onReleaseMicState(ECError reason) {

								if (reason.errorCode
										.equals(SDKCoreHelper.NO_ERROR)) {
									mChronometer.stop();
									mChronometer.setVisibility(View.GONE);
								}
								// if(reason != 0 && checkeDeviceHelper())
								// getDeviceHelper().releaseMic(confNo);

								// Regardless of whether the successful release
								// of wheat, wheat
								// UI pictures of local changes
								mInterSpeak
										.setBackgroundResource(R.drawable.voice_intephone_normal);
								UpdateViewUI(
										new String[] { CCPConfig.VoIP_ID },
										MESSAGE_TYPE_RELEASE_MIC);

							}
						});

				downTime = 0;
			}
			mNoticeTips.setText(R.string.top_tips_intercom_ing);
			break;
		}
		return false;
	}

	@Override
	protected void handleTitleAction(int direction) {
		if (direction == TITLE_RIGHT_ACTION) {

			ECDevice.getECMeetingManager().exitMeeting();
			ECDevice.getECVoipSetManager().enableLoudSpeaker(false);

			if (ECApplication.interphoneIds != null
					&& !ECApplication.interphoneIds.isEmpty()) {
				ECApplication.interphoneIds.remove(this.confNo);
			}

			finish();

		} else {
			super.handleTitleAction(direction);
		}
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();

		ECDevice.getECMeetingManager().exitMeeting();
		ECDevice.getECVoipSetManager().enableLoudSpeaker(false);
	}

	class InterPhoneMemberAdapter extends
			ArrayAdapter<ECInterphoneMeetingMember> {

		LayoutInflater mInflater;

		public InterPhoneMemberAdapter(Context context,
				List<ECInterphoneMeetingMember> objects) {
			super(context, 0, objects);

			mInflater = getLayoutInflater();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			InterJoinMemberHolder holder;
			if (convertView == null || convertView.getTag() == null) {
				convertView = mInflater.inflate(
						R.layout.into_interphone_member_list_item, null);
				holder = new InterJoinMemberHolder();

				holder.statuIcon = (ImageView) convertView
						.findViewById(R.id.interphone_join_statu);
				holder.name = (TextView) convertView.findViewById(R.id.name);
				holder.actionTips = (TextView) convertView
						.findViewById(R.id.action_tips);
			} else {
				holder = (InterJoinMemberHolder) convertView.getTag();
			}

			// do ..
			ECInterphoneMeetingMember phoneMember = mJoinMembers.get(position);
			if (phoneMember != null) {
				holder.name.setText(phoneMember.voipId);

				String control = phoneMember.mic;
				String online = phoneMember.online;

				int resoureId = 0;
				int tipsId = 0;
				if (TYPE_ONLINE.equals(online)) {
					if (TYPE_SPEAK_INITIATOR.equals(control)) {
						resoureId = R.drawable.status_speaking;
						tipsId = R.string.str_join_speaking;
					} else {
						resoureId = R.drawable.status_join;
						tipsId = R.string.str_join_success;
					}
				} else {
					resoureId = R.drawable.status_wait;
					tipsId = R.string.str_join_wait;
				}

				if (CCPConfig.VoIP_ID.equals(phoneMember.voipId)) {
					resoureId = R.drawable.inter_person_icon;
				}
				holder.statuIcon.setImageResource(resoureId);
				holder.actionTips.setText(tipsId);
			}

			return convertView;
		}

		class InterJoinMemberHolder {
			ImageView statuIcon;
			TextView name;
			TextView actionTips;
		}
	}

	private String confNo;

	// Callback handler, according to the interphone state,
	// update the display interface
	private android.os.Handler mHandler = new android.os.Handler() {

		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			Bundle b = null;
			int reason = 0;
			ECInterphoneMeetingMsg ipmsg = null;
			ArrayList<ECInterphoneMeetingMember> members = null;
			if (msg.obj instanceof Bundle) {
				b = (Bundle) msg.obj;
				reason = b.getInt(ECGlobalConstants.REASON);
				if (b.getString(ECGlobalConstants.CONFNO) != null)
					confNo = b.getString(ECGlobalConstants.CONFNO);
				if (b.getParcelable(ECGlobalConstants.INTERPHONEMSG) != null)
					ipmsg = (ECInterphoneMeetingMsg) b
							.getParcelable(ECGlobalConstants.INTERPHONEMSG);

				if (b.getParcelable(ECGlobalConstants.MEMBERS) != null)
					members = (ArrayList<ECInterphoneMeetingMember>) b
							.getParcelable(ECGlobalConstants.MEMBERS);
			}

			switch (msg.what) {
			case SDKCoreHelper.WHAT_ON_INTERPHONE:
				reason = b.getInt(ECGlobalConstants.REASON);

				break;
			case SDKCoreHelper.WHAT_ON_INTERPHONE_MEMBERS:

				break;
			case SDKCoreHelper.WHAT_ON_CONTROL_MIC:
				reason = b.getInt(ECGlobalConstants.REASON);
				if (reason == 0) {
					// control mic success ..
					try {
						CCPUtil.playNotifycationMusic(getApplicationContext(),
								"inter_phone_connect.mp3");
					} catch (IOException e) {
						e.printStackTrace();
					}
					mChronometer.setBase(SystemClock.elapsedRealtime());
					mChronometer.setVisibility(View.VISIBLE);
					mChronometer.start();
					mNoticeTips.setText(R.string.str_control_mic_success);
					mInterSpeak
							.setBackgroundResource(R.drawable.voice_intephone_connect);
					UpdateViewUI(new String[] { CCPAppManager.getClientUser()
							.getUserId() }, MESSAGE_TYPE_CONTROL_MIC);

				} else {
					// failed ..
					if (isDownEvent) {
						mInterSpeak
								.setBackgroundResource(R.drawable.voice_intephone_failed);
					} else {
						mInterSpeak
								.setBackgroundResource(R.drawable.voice_intephone_normal);
					}
					mNoticeTips.setText(R.string.str_control_mic_failed);
				}
				break;
			case SDKCoreHelper.WHAT_ON_RELEASE_MIC:

				break;
			case SDKCoreHelper.WHAT_ON_INTERPHONE_SIP_MESSAGE:

				try {
					if (ipmsg != null) {
						if (ipmsg instanceof ECInterphoneInviteMsg) {
							ECInterphoneInviteMsg ipi = (ECInterphoneInviteMsg) ipmsg;
							mNoticeTips.setText(getString(
									R.string.str_invite_join_inter, ipi.from));
						} else if (ipmsg instanceof ECInterphoneJoinMsg) {
							ECInterphoneJoinMsg ipj = (ECInterphoneJoinMsg) ipmsg;
							increase(ipj.whos.length);
							UpdateViewUI(ipj.whos, MESSAGE_TYPE_JOIN);
						} else if (ipmsg instanceof ECInterphoneExitMsg) {
							ECInterphoneExitMsg ipe = (ECInterphoneExitMsg) ipmsg;
							decrease(ipe.whos.length);
							UpdateViewUI(ipe.whos, MESSAGE_TYPE_EXIT);
						} else if (ipmsg instanceof ECInterphoneOverMsg) {

						} else if (ipmsg instanceof ECInterphoneControlMicMsg) {

							ECInterphoneControlMicMsg mic = (ECInterphoneControlMicMsg) ipmsg;
							UpdateViewUI(new String[] { mic.who },
									MESSAGE_TYPE_CONTROL_MIC);
							mChronometer.setBase(SystemClock.elapsedRealtime());
							mChronometer.setVisibility(View.VISIBLE);
							mChronometer.start();
						} else if (ipmsg instanceof ECInterphoneReleaseMicMsg) {
							ECInterphoneReleaseMicMsg mic = (ECInterphoneReleaseMicMsg) ipmsg;
							UpdateViewUI(new String[] { mic.who },
									MESSAGE_TYPE_RELEASE_MIC);
							mChronometer.stop();
							mChronometer.setVisibility(View.GONE);

						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			case SDKCoreHelper.WHAT_ON_REQUEST_MIC_CONTROL:
				ECDevice.getECMeetingManager().controlMicInInterphoneMeeting(
						confNo, new OnControlMicInInterphoneListener() {

							@Override
							public void onControlMicState(ECError reason,
									String speaker) {

								if (reason.errorCode
										.equals(SDKCoreHelper.NO_ERROR)) {
									// control mic success ..
									try {
										CCPUtil.playNotifycationMusic(
												getApplicationContext(),
												"inter_phone_connect.mp3");
									} catch (IOException e) {
										e.printStackTrace();
									}
									mChronometer.setBase(SystemClock
											.elapsedRealtime());
									mChronometer.setVisibility(View.VISIBLE);
									mChronometer.start();
									mNoticeTips
											.setText(R.string.str_control_mic_success);
									mInterSpeak
											.setBackgroundResource(R.drawable.voice_intephone_connect);
									UpdateViewUI(
											new String[] { CCPConfig.VoIP_ID },
											MESSAGE_TYPE_CONTROL_MIC);

								} else {
									// failed ..
									if (isDownEvent) {
										mInterSpeak
												.setBackgroundResource(R.drawable.voice_intephone_failed);
									} else {
										mInterSpeak
												.setBackgroundResource(R.drawable.voice_intephone_normal);
									}
									mNoticeTips
											.setText(R.string.str_control_mic_failed);
								}

							}
						});

				mNoticeTips.setText(R.string.top_tips_connecting_wait);

				break;
			case SDKCoreHelper.WHAT_ON_PLAY_MUSIC:
				try {
					CCPUtil.playNotifycationMusic(getApplicationContext(),
							"inter_phone_pressed.mp3");
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case SDKCoreHelper.WHAT_ON_STOP_MUSIC:
				try {
					mChronometer.stop();
					mChronometer.setVisibility(View.GONE);
					CCPUtil.playNotifycationMusic(getApplicationContext(),
							"inter_phone_up.mp3");
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			default:
				break;
			}
		}
	};

	// According to handle and type to update the UI callback interface.
	void UpdateViewUI(String[] whos, int type) {

		if (mJoinMembers != null) {
			for (int i = 0; i < whos.length; i++) {
				StringBuilder text = new StringBuilder(whos[i]);
				ECInterphoneMeetingMember remove = null;
				int index = -1;
				for (ECInterphoneMeetingMember interphoneMember : mJoinMembers) {
					if (interphoneMember.voipId.equals(whos[i])) {
						index = mJoinMembers.indexOf(interphoneMember);
						if (mJoinMembers.indexOf(interphoneMember) >= 0) {
							remove = mJoinMembers.remove(index);
						}

						break;
					}
				}
				if (remove != null) {
					if (type == MESSAGE_TYPE_JOIN) {
						remove.online = TYPE_ONLINE;
						text.append(getString(R.string.str_join_inter_phone_success));
						// increase();
					} else if (type == MESSAGE_TYPE_EXIT) {
						remove.online = TYPE_UNONLINE;
						text.append(getString(R.string.str_quit_inter_phone));
						// decrease();
					} else if (type == MESSAGE_TYPE_CONTROL_MIC) {
						remove.mic = TYPE_MIC_CONTROLER;
						text.append(getString(R.string.str_speaking));
						for (ECInterphoneMeetingMember member : mJoinMembers) {
							member.mic = TYPE_MIC_UNCONTROL;
						}
					} else if (type == MESSAGE_TYPE_RELEASE_MIC) {
						text = new StringBuilder(
								getString(R.string.str_can_control_mic));
						remove.mic = TYPE_MIC_UNCONTROL;
					} else {
						text = new StringBuilder(
								getString(R.string.str_can_control_mic));
					}

					mJoinMembers.add(index, remove);
					mNoticeTips.setText(text.toString());
				}

			}
			if (mPersonCount != null && mJoinMembers != null) {
				mPersonCount.setText(onLineNum + "/" + mJoinMembers.size());
			}
		}
		if (mIPoneMemAdapter != null) {
			mIPoneMemAdapter.notifyDataSetChanged();
		}

	}

	boolean isSpeakerphoneOn = true;

	@Override
	protected void onResume() {
		super.onResume();
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		if (!mAudioManager.isSpeakerphoneOn()) {
			mAudioManager.setSpeakerphoneOn(true);
			isSpeakerphoneOn = false;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mAudioManager != null) {
			mAudioManager.setSpeakerphoneOn(isSpeakerphoneOn);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mJoinMembers != null) {
			mJoinMembers.clear();
			mJoinMembers = null;
		}
		if (mHandler != null) {
			mHandler = null;
		}

	}

	// talkback member online / total number of Statistics
	synchronized void increase(int inc) {
		onLineNum = onLineNum + inc;
	}

	synchronized void decrease(int dec) {
		if (onLineNum - dec >= 0) {
			onLineNum = onLineNum - dec;
		} else {
			onLineNum = 0;
		}
	}

	@Override
	protected int getLayoutId() {
		return R.layout.layout_interphone_room_activity;
	}

	@Override
	public int getTitleLayout() {
		// TODO Auto-generated method stub
		return -1;
	}
}
