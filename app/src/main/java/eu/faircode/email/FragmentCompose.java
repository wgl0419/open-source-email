package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018 by Marcel Bokhorst (M66B)
*/

import android.Manifest;
import android.app.PendingIntent;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.jsoup.Jsoup;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.xml.sax.XMLReader;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.MessageRemovedException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static android.app.Activity.RESULT_OK;

public class FragmentCompose extends FragmentEx {
    private ViewGroup view;
    private Spinner spAccount;
    private Spinner spIdentity;
    private ImageView ivIdentityAdd;
    private TextView tvExtraPrefix;
    private EditText etExtra;
    private TextView tvExtraSuffix;
    private MultiAutoCompleteTextView etTo;
    private ImageView ivToAdd;
    private MultiAutoCompleteTextView etCc;
    private ImageView ivCcAdd;
    private MultiAutoCompleteTextView etBcc;
    private ImageView ivBccAdd;
    private EditText etSubject;
    private RecyclerView rvAttachment;
    private EditText etBody;
    private TextView tvSignature;
    private TextView tvReference;
    private BottomNavigationView edit_bar;
    private BottomNavigationView bottom_navigation;
    private ProgressBar pbWait;
    private Group grpHeader;
    private Group grpExtra;
    private Group grpAddresses;
    private Group grpAttachments;
    private Group grpSignature;
    private Group grpReference;

    private AdapterAttachment adapter;

    private long working = -1;
    private boolean busy = false;
    private boolean autosave = false;
    private boolean pro = false;

    private OpenPgpServiceConnection pgpService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pro = Helper.isPro(getContext());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = (ViewGroup) inflater.inflate(R.layout.fragment_compose, container, false);

        // Get controls
        spAccount = view.findViewById(R.id.spAccount);
        spIdentity = view.findViewById(R.id.spIdentity);
        ivIdentityAdd = view.findViewById(R.id.ivIdentityAdd);
        tvExtraPrefix = view.findViewById(R.id.tvExtraPrefix);
        etExtra = view.findViewById(R.id.etExtra);
        tvExtraSuffix = view.findViewById(R.id.tvExtraSuffix);
        etTo = view.findViewById(R.id.etTo);
        ivToAdd = view.findViewById(R.id.ivToAdd);
        etCc = view.findViewById(R.id.etCc);
        ivCcAdd = view.findViewById(R.id.ivCcAdd);
        etBcc = view.findViewById(R.id.etBcc);
        ivBccAdd = view.findViewById(R.id.ivBccAdd);
        etSubject = view.findViewById(R.id.etSubject);
        rvAttachment = view.findViewById(R.id.rvAttachment);
        etBody = view.findViewById(R.id.etBody);
        tvSignature = view.findViewById(R.id.tvSignature);
        tvReference = view.findViewById(R.id.tvReference);
        edit_bar = view.findViewById(R.id.edit_bar);
        bottom_navigation = view.findViewById(R.id.bottom_navigation);
        pbWait = view.findViewById(R.id.pbWait);
        grpHeader = view.findViewById(R.id.grpHeader);
        grpExtra = view.findViewById(R.id.grpExtra);
        grpAddresses = view.findViewById(R.id.grpAddresses);
        grpAttachments = view.findViewById(R.id.grpAttachments);
        grpSignature = view.findViewById(R.id.grpSignature);
        grpReference = view.findViewById(R.id.grpReference);

        // Wire controls
        spIdentity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EntityIdentity identity = (EntityIdentity) parent.getAdapter().getItem(position);
                int at = (identity == null ? -1 : identity.email.indexOf('@'));
                tvExtraPrefix.setText(at < 0 ? null : identity.email.substring(0, at));
                tvExtraSuffix.setText(at < 0 ? null : identity.email.substring(at));
                if (pro) {
                    tvSignature.setText(identity == null ? null : Html.fromHtml(identity.signature));
                    grpSignature.setVisibility(identity == null || TextUtils.isEmpty(identity.signature) ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                tvExtraPrefix.setText(null);
                tvExtraSuffix.setText(null);
                tvSignature.setText(null);
                grpSignature.setVisibility(View.GONE);
            }
        });

        ivIdentityAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentIdentity fragment = new FragmentIdentity();
                fragment.setArguments(new Bundle());

                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("identity");
                fragmentTransaction.commit();
            }
        });

        View.OnClickListener onPick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int request;
                switch (view.getId()) {
                    case R.id.ivToAdd:
                        request = ActivityCompose.REQUEST_CONTACT_TO;
                        break;
                    case R.id.ivCcAdd:
                        request = ActivityCompose.REQUEST_CONTACT_CC;
                        break;
                    case R.id.ivBccAdd:
                        request = ActivityCompose.REQUEST_CONTACT_BCC;
                        break;
                    default:
                        return;
                }

                Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI);
                if (pick.resolveActivity(getContext().getPackageManager()) == null)
                    Snackbar.make(view, R.string.title_no_contacts, Snackbar.LENGTH_LONG).show();
                else
                    startActivityForResult(Helper.getChooser(getContext(), pick), request);
            }
        };

        ivToAdd.setOnClickListener(onPick);
        ivCcAdd.setOnClickListener(onPick);
        ivBccAdd.setOnClickListener(onPick);

        edit_bar.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int action = item.getItemId();
                switch (action) {
                    case R.id.menu_bold:
                    case R.id.menu_italic:
                    case R.id.menu_clear:
                    case R.id.menu_link:
                        onMenuStyle(item.getItemId());
                        return true;
                    case R.id.menu_image:
                        onMenuImage();
                        return true;
                    case R.id.menu_attachment:
                        onMenuAttachment();
                        return true;
                    default:
                        return false;
                }
            }
        });

        bottom_navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int action = item.getItemId();
                switch (action) {
                    case R.id.action_delete:
                        onDelete();
                        break;
                    default:
                        onAction(action);
                }
                return false;
            }
        });

        ((ActivityBase) getActivity()).addBackPressedListener(onBackPressedListener);

        setHasOptionsMenu(true);

        // Initialize
        setSubtitle(R.string.title_compose);
        tvExtraPrefix.setText(null);
        tvExtraSuffix.setText(null);

        grpHeader.setVisibility(View.GONE);
        grpExtra.setVisibility(View.GONE);
        grpAddresses.setVisibility(View.GONE);
        grpAttachments.setVisibility(View.GONE);
        etBody.setVisibility(View.GONE);
        grpSignature.setVisibility(View.GONE);
        grpReference.setVisibility(View.GONE);
        edit_bar.setVisibility(View.GONE);
        bottom_navigation.setVisibility(View.GONE);
        pbWait.setVisibility(View.VISIBLE);

        getActivity().invalidateOptionsMenu();
        Helper.setViewsEnabled(view, false);

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                    getContext(),
                    android.R.layout.simple_list_item_2,
                    null,
                    new String[]{
                            ContactsContract.Contacts.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Email.DATA
                    },
                    new int[]{
                            android.R.id.text1,
                            android.R.id.text2
                    },
                    0);

            etTo.setAdapter(adapter);
            etCc.setAdapter(adapter);
            etBcc.setAdapter(adapter);

            etTo.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
            etCc.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
            etBcc.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());

            adapter.setFilterQueryProvider(new FilterQueryProvider() {
                public Cursor runQuery(CharSequence typed) {
                    return getContext().getContentResolver().query(
                            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                            new String[]{
                                    ContactsContract.RawContacts._ID,
                                    ContactsContract.Contacts.DISPLAY_NAME,
                                    ContactsContract.CommonDataKinds.Email.DATA
                            },
                            ContactsContract.CommonDataKinds.Email.DATA + " <> ''" +
                                    " AND (" + ContactsContract.Contacts.DISPLAY_NAME + " LIKE '%" + typed + "%'" +
                                    " OR " + ContactsContract.CommonDataKinds.Email.DATA + " LIKE '%" + typed + "%')",
                            null,
                            "CASE WHEN " + ContactsContract.Contacts.DISPLAY_NAME + " NOT LIKE '%@%' THEN 0 ELSE 1 END" +
                                    ", " + ContactsContract.Contacts.DISPLAY_NAME +
                                    ", " + ContactsContract.CommonDataKinds.Email.DATA + " COLLATE NOCASE");
                }
            });

            adapter.setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter() {
                public CharSequence convertToString(Cursor cursor) {
                    int colName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                    int colEmail = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
                    String name = cursor.getString(colName);
                    String email = cursor.getString(colEmail);
                    StringBuilder sb = new StringBuilder();
                    if (name == null)
                        sb.append(email);
                    else {
                        sb.append(name.replace(",", "")).append(" ");
                        sb.append("<").append(email).append(">");
                    }
                    return sb.toString();
                }
            });
        }

        rvAttachment.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        rvAttachment.setLayoutManager(llm);
        rvAttachment.setItemAnimator(null);

        adapter = new AdapterAttachment(getContext(), getViewLifecycleOwner(), false);
        rvAttachment.setAdapter(adapter);

        pgpService = new OpenPgpServiceConnection(getContext(), "org.sufficientlysecure.keychain");
        pgpService.bindToService();

        return view;
    }

    @Override
    public void onDestroyView() {
        adapter = null;

        if (pgpService != null)
            pgpService.unbindFromService();

        ((ActivityBase) getActivity()).removeBackPressedListener(onBackPressedListener);

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("working", working);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState == null) {
            if (working < 0) {
                Bundle args = new Bundle();
                args.putString("action", getArguments().getString("action"));
                args.putLong("id", getArguments().getLong("id", -1));
                args.putLong("account", getArguments().getLong("account", -1));
                args.putLong("reference", getArguments().getLong("reference", -1));
                args.putBoolean("raw", getArguments().getBoolean("raw", false));
                args.putLong("answer", getArguments().getLong("answer", -1));
                args.putString("to", getArguments().getString("to"));
                args.putString("cc", getArguments().getString("cc"));
                args.putString("bcc", getArguments().getString("bcc"));
                args.putString("subject", getArguments().getString("subject"));
                args.putString("body", getArguments().getString("body"));
                args.putParcelableArrayList("attachments", getArguments().getParcelableArrayList("attachments"));
                draftLoader.load(this, args);
            } else {
                Bundle args = new Bundle();
                args.putString("action", "edit");
                args.putLong("id", working);
                args.putLong("account", -1);
                args.putLong("reference", -1);
                args.putLong("answer", -1);
                draftLoader.load(this, args);
            }
        } else {
            working = savedInstanceState.getLong("working");
            Bundle args = new Bundle();
            args.putString("action", working < 0 ? "new" : "edit");
            args.putLong("id", working);
            args.putLong("account", -1);
            args.putLong("reference", -1);
            args.putLong("answer", -1);
            draftLoader.load(this, args);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!pgpService.isBound())
            pgpService.bindToService();
    }

    @Override
    public void onPause() {
        if (autosave)
            onAction(R.id.action_save);
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_compose, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_addresses).setVisible(working >= 0);
        menu.findItem(R.id.menu_clear).setVisible(working >= 0);
        menu.findItem(R.id.menu_encrypt).setVisible(working >= 0);

        menu.findItem(R.id.menu_clear).setEnabled(!busy);
        menu.findItem(R.id.menu_encrypt).setEnabled(!busy);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                    handleExit();
                return true;
            case R.id.menu_addresses:
                onMenuAddresses();
                return true;
            case R.id.menu_clear:
                onMenuStyle(item.getItemId());
                return true;
            case R.id.menu_encrypt:
                onAction(R.id.menu_encrypt);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onMenuStyle(int id) {
        int start = etBody.getSelectionStart();
        int end = etBody.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start == end)
            Snackbar.make(view, R.string.title_no_selection, Snackbar.LENGTH_LONG).show();
        else {
            SpannableString s = new SpannableString(etBody.getText());
            switch (id) {
                case R.id.menu_bold:
                    s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case R.id.menu_italic:
                    s.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case R.id.menu_clear:
                    for (Object span : s.getSpans(start, end, Object.class))
                        if (!(span instanceof ImageSpan))
                            s.removeSpan(span);
                    break;
                case R.id.menu_link:
                    Uri uri = null;
                    ClipboardManager cbm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cbm.hasPrimaryClip()) {
                        String link = cbm.getPrimaryClip().getItemAt(0).coerceToText(getContext()).toString();
                        uri = Uri.parse(link);
                        if (uri.getScheme() == null)
                            uri = null;
                    }
                    if (uri == null)
                        Snackbar.make(view, R.string.title_clipboard_empty, Snackbar.LENGTH_LONG).show();
                    else
                        s.setSpan(new URLSpan(uri.toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
            }
            etBody.setText(s);
            etBody.setSelection(end);
        }
    }

    private void onMenuImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        PackageManager pm = getContext().getPackageManager();
        if (intent.resolveActivity(pm) == null)
            Snackbar.make(view, R.string.title_no_saf, Snackbar.LENGTH_LONG).show();
        else
            startActivityForResult(Helper.getChooser(getContext(), intent), ActivityCompose.REQUEST_IMAGE);
    }

    private void onMenuAttachment() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        PackageManager pm = getContext().getPackageManager();
        if (intent.resolveActivity(pm) == null)
            Snackbar.make(view, R.string.title_no_saf, Snackbar.LENGTH_LONG).show();
        else
            startActivityForResult(Helper.getChooser(getContext(), intent), ActivityCompose.REQUEST_ATTACHMENT);
    }

    private void onMenuAddresses() {
        grpAddresses.setVisibility(grpAddresses.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
    }

    private void onDelete() {
        new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                .setMessage(R.string.title_ask_discard)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onAction(R.id.action_delete);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onEncrypt() {
        if (Helper.isPro(getContext())) {
            if (pgpService.isBound())
                try {
                    String to = etTo.getText().toString();
                    InternetAddress ato[] = (TextUtils.isEmpty(to) ? new InternetAddress[0] : InternetAddress.parse(to));
                    if (ato.length == 0)
                        throw new IllegalArgumentException(getString(R.string.title_to_missing));

                    String[] tos = new String[ato.length];
                    for (int i = 0; i < ato.length; i++)
                        tos[i] = ato[i].getAddress();

                    Intent data = new Intent();
                    data.setAction(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT);
                    data.putExtra(OpenPgpApi.EXTRA_USER_IDS, tos);
                    data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

                    encrypt(data);
                } catch (Throwable ex) {
                    if (ex instanceof IllegalArgumentException)
                        Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                    else
                        Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
                }
            else {
                Snackbar snackbar = Snackbar.make(view, R.string.title_no_openpgp, Snackbar.LENGTH_LONG);
                if (Helper.getIntentOpenKeychain().resolveActivity(getContext().getPackageManager()) != null)
                    snackbar.setAction(R.string.title_fix, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(Helper.getIntentOpenKeychain());
                        }
                    });
                snackbar.show();
            }
        } else {
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.content_frame, new FragmentPro()).addToBackStack("pro");
            fragmentTransaction.commit();
        }
    }

    private void encrypt(Intent data) {
        final Bundle args = new Bundle();
        args.putLong("id", working);
        args.putParcelable("data", data);

        new SimpleTask<PendingIntent>() {
            @Override
            protected PendingIntent onLoad(Context context, Bundle args) throws Throwable {
                // Get arguments
                long id = args.getLong("id");
                Intent data = args.getParcelable("data");

                DB db = DB.getInstance(context);

                // Get attachments
                EntityMessage message = db.message().getMessage(id);
                List<EntityAttachment> attachments = db.attachment().getAttachments(id);
                for (EntityAttachment attachment : new ArrayList<>(attachments))
                    if ("encrypted.asc".equals(attachment.name) || "signature.asc".equals(attachment.name))
                        attachments.remove(attachment);

                // Build message
                Properties props = MessageHelper.getSessionProperties(Helper.AUTH_TYPE_PASSWORD, false);
                Session isession = Session.getInstance(props, null);
                MimeMessage imessage = new MimeMessage(isession);
                MessageHelper.build(context, message, imessage);

                // Serialize message
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                imessage.writeTo(os);
                ByteArrayInputStream decrypted = new ByteArrayInputStream(os.toByteArray());
                ByteArrayOutputStream encrypted = new ByteArrayOutputStream();

                // Encrypt message
                OpenPgpApi api = new OpenPgpApi(context, pgpService.getService());
                Intent result = api.executeApi(data, decrypted, encrypted);
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        // Get public signature
                        Intent keyRequest = new Intent();
                        keyRequest.setAction(OpenPgpApi.ACTION_DETACHED_SIGN);
                        keyRequest.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, data.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, -1));
                        Intent key = api.executeApi(keyRequest, decrypted, null);
                        int r = key.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR);
                        if (r != OpenPgpApi.RESULT_CODE_SUCCESS) {
                            OpenPgpError error = key.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                            throw new IllegalArgumentException(error.getMessage());
                        }

                        // Attach encrypted data
                        try {
                            db.beginTransaction();

                            // Delete previously encrypted data
                            for (EntityAttachment attachment : db.attachment().getAttachments(id))
                                if ("encrypted.asc".equals(attachment.name) || "signature.asc".equals(attachment.name))
                                    db.attachment().deleteAttachment(attachment.id);

                            int seq = db.attachment().getAttachmentSequence(id);

                            EntityAttachment attachment1 = new EntityAttachment();
                            attachment1.message = id;
                            attachment1.sequence = seq + 1;
                            attachment1.name = "encrypted.asc";
                            attachment1.type = "application/octet-stream";
                            attachment1.id = db.attachment().insertAttachment(attachment1);

                            File file1 = EntityAttachment.getFile(context, attachment1.id);

                            OutputStream os1 = null;
                            try {
                                byte[] bytes1 = encrypted.toByteArray();
                                os1 = new BufferedOutputStream(new FileOutputStream(file1));
                                os1.write(bytes1);

                                attachment1.size = bytes1.length;
                                attachment1.progress = null;
                                attachment1.available = true;
                                db.attachment().updateAttachment(attachment1);
                            } finally {
                                if (os1 != null)
                                    os1.close();
                            }

                            EntityAttachment attachment2 = new EntityAttachment();
                            attachment2.message = id;
                            attachment2.sequence = seq + 2;
                            attachment2.name = "signature.asc";
                            attachment2.type = "application/octet-stream";
                            attachment2.id = db.attachment().insertAttachment(attachment2);

                            File file2 = EntityAttachment.getFile(context, attachment2.id);

                            OutputStream os2 = null;
                            try {
                                byte[] bytes2 = key.getByteArrayExtra(OpenPgpApi.RESULT_DETACHED_SIGNATURE);
                                os2 = new BufferedOutputStream(new FileOutputStream(file2));
                                os2.write(bytes2);

                                attachment2.size = bytes2.length;
                                attachment2.progress = null;
                                attachment2.available = true;
                                db.attachment().updateAttachment(attachment2);
                            } finally {
                                if (os2 != null)
                                    os2.close();
                            }

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }

                        break;

                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        return result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);

                    case OpenPgpApi.RESULT_CODE_ERROR:
                        OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                        throw new IllegalArgumentException(error.getMessage());
                }

                return null;
            }

            @Override
            protected void onLoaded(Bundle args, PendingIntent pi) {
                if (pi != null)
                    try {
                        startIntentSenderForResult(
                                pi.getIntentSender(),
                                ActivityCompose.REQUEST_ENCRYPT,
                                null, 0, 0, 0, null);
                    } catch (IntentSender.SendIntentException ex) {
                        Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
                    }
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                if (ex instanceof IllegalArgumentException)
                    Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                else
                    Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
            }
        }.load(this, args);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == ActivityCompose.REQUEST_IMAGE) {
                if (data != null)
                    handleAddAttachment(data, true);
            } else if (requestCode == ActivityCompose.REQUEST_ATTACHMENT) {
                if (data != null)
                    handleAddAttachment(data, false);
            } else if (requestCode == ActivityCompose.REQUEST_ENCRYPT) {
                if (data != null) {
                    data.setAction(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT);
                    encrypt(data);
                }
            } else {
                if (data != null)
                    handlePickContact(requestCode, data);
            }
        }
    }

    private void handlePickContact(int requestCode, Intent data) {
        Cursor cursor = null;
        try {
            Uri uri = data.getData();
            if (uri != null)
                cursor = getContext().getContentResolver().query(uri,
                        new String[]{
                                ContactsContract.CommonDataKinds.Email.ADDRESS,
                                ContactsContract.Contacts.DISPLAY_NAME
                        },
                        null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int colEmail = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);
                int colName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                String email = cursor.getString(colEmail);
                String name = cursor.getString(colName);

                String text = null;
                if (requestCode == ActivityCompose.REQUEST_CONTACT_TO)
                    text = etTo.getText().toString();
                else if (requestCode == ActivityCompose.REQUEST_CONTACT_CC)
                    text = etCc.getText().toString();
                else if (requestCode == ActivityCompose.REQUEST_CONTACT_BCC)
                    text = etBcc.getText().toString();

                InternetAddress address = new InternetAddress(email, name);
                StringBuilder sb = new StringBuilder(text);
                sb.append(address.toString().replace(",", "")).append(", ");

                if (requestCode == ActivityCompose.REQUEST_CONTACT_TO)
                    etTo.setText(sb.toString());
                else if (requestCode == ActivityCompose.REQUEST_CONTACT_CC)
                    etCc.setText(sb.toString());
                else if (requestCode == ActivityCompose.REQUEST_CONTACT_BCC)
                    etBcc.setText(sb.toString());
            }
        } catch (Throwable ex) {
            Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
            Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    private void handleAddAttachment(Intent data, final boolean image) {
        Uri uri = data.getData();
        if (uri == null)
            return;

        Bundle args = new Bundle();
        args.putLong("id", working);
        args.putParcelable("uri", data.getData());

        new SimpleTask<EntityAttachment>() {
            @Override
            protected EntityAttachment onLoad(Context context, Bundle args) throws IOException {
                Long id = args.getLong("id");
                Uri uri = args.getParcelable("uri");
                return addAttachment(context, id, uri, image);
            }

            @Override
            protected void onLoaded(Bundle args, EntityAttachment attachment) {
                if (image) {
                    File file = EntityAttachment.getFile(getContext(), attachment.id);
                    Drawable d = Drawable.createFromPath(file.getAbsolutePath());
                    d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());

                    int start = etBody.getSelectionStart();
                    etBody.getText().insert(start, " ");
                    SpannableString s = new SpannableString(etBody.getText());
                    ImageSpan is = new ImageSpan(getContext(), Uri.parse("cid:" + BuildConfig.APPLICATION_ID + "." + attachment.id), ImageSpan.ALIGN_BASELINE);
                    s.setSpan(is, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    String html = Html.toHtml(s);
                    etBody.setText(Html.fromHtml(html, cidGetter, null));
                }
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                // External app sending absolute file
                if (ex instanceof FileNotFoundException)
                    Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                else
                    Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
            }
        }.load(this, args);
    }

    private void handleExit() {
        if (isEmpty())
            onAction(R.id.action_delete);
        else
            new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                    .setMessage(R.string.title_ask_discard)
                    .setPositiveButton(R.string.title_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            onAction(R.id.action_delete);
                        }
                    })
                    .setNegativeButton(R.string.title_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            finish();
                        }
                    })
                    .show();
    }

    private void onAction(int action) {
        busy = true;
        Helper.setViewsEnabled(view, false);
        getActivity().invalidateOptionsMenu();

        EntityIdentity identity = (EntityIdentity) spIdentity.getSelectedItem();

        Bundle args = new Bundle();
        args.putLong("id", working);
        args.putInt("action", action);
        args.putLong("account", identity == null ? -1 : identity.account);
        args.putLong("identity", identity == null ? -1 : identity.id);
        args.putString("extra", etExtra.getText().toString());
        args.putString("to", etTo.getText().toString());
        args.putString("cc", etCc.getText().toString());
        args.putString("bcc", etBcc.getText().toString());
        args.putString("subject", etSubject.getText().toString());
        args.putBoolean("empty", isEmpty());

        Spannable spannable = etBody.getText();
        UnderlineSpan[] uspans = spannable.getSpans(0, spannable.length(), UnderlineSpan.class);
        for (UnderlineSpan uspan : uspans)
            spannable.removeSpan(uspan);

        args.putString("body", Html.toHtml(spannable));

        Log.i(Helper.TAG, "Run load id=" + working);
        actionLoader.load(this, args);
    }

    private boolean isEmpty() {
        if (!TextUtils.isEmpty(etExtra.getText().toString().trim()))
            return false;
        if (!TextUtils.isEmpty(etTo.getText().toString().trim()))
            return false;
        if (!TextUtils.isEmpty(etCc.getText().toString().trim()))
            return false;
        if (!TextUtils.isEmpty(etBcc.getText().toString().trim()))
            return false;
        if (!TextUtils.isEmpty(etSubject.getText().toString().trim()))
            return false;
        if (!TextUtils.isEmpty(Jsoup.parse(Html.toHtml(etBody.getText())).text().trim()))
            return false;
        if (rvAttachment.getAdapter().getItemCount() > 0)
            return false;
        return true;
    }

    private static EntityAttachment addAttachment(Context context, long id, Uri uri,
                                                  boolean image) throws IOException {
        EntityAttachment attachment = new EntityAttachment();

        String name = null;
        String s = null;

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                s = cursor.getString(cursor.getColumnIndex(OpenableColumns.SIZE));
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }

        DB db = DB.getInstance(context);
        try {
            db.beginTransaction();

            EntityMessage draft = db.message().getMessage(id);
            Log.i(Helper.TAG, "Attaching to id=" + id);

            attachment.message = draft.id;
            attachment.sequence = db.attachment().getAttachmentSequence(draft.id) + 1;
            attachment.name = name;

            String extension = Helper.getExtension(attachment.name);
            if (extension != null)
                attachment.type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (attachment.type == null)
                attachment.type = "application/octet-stream";

            attachment.size = (s == null ? null : Integer.parseInt(s));
            attachment.progress = 0;

            attachment.id = db.attachment().insertAttachment(attachment);
            Log.i(Helper.TAG, "Created attachment=" + attachment.name + ":" + attachment.sequence + " type=" + attachment.type);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        try {
            File file = EntityAttachment.getFile(context, attachment.id);

            InputStream is = null;
            OutputStream os = null;
            try {
                is = context.getContentResolver().openInputStream(uri);
                os = new BufferedOutputStream(new FileOutputStream(file));

                int size = 0;
                byte[] buffer = new byte[EntityAttachment.ATTACHMENT_BUFFER_SIZE];
                for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
                    size += len;
                    os.write(buffer, 0, len);

                    // Update progress
                    if (attachment.size != null)
                        db.attachment().setProgress(attachment.id, size * 100 / attachment.size);
                }

                if (image)
                    attachment.cid = "<" + BuildConfig.APPLICATION_ID + "." + attachment.id + ">";

                attachment.size = size;
                attachment.progress = null;
                attachment.available = true;
                db.attachment().updateAttachment(attachment);
            } finally {
                try {
                    if (is != null)
                        is.close();
                } finally {
                    if (os != null)
                        os.close();
                }
            }
        } catch (IOException ex) {
            // Reset progress on failure
            attachment.progress = null;
            db.attachment().updateAttachment(attachment);
            throw ex;
        }

        return attachment;
    }

    private SimpleTask<DraftAccount> draftLoader = new SimpleTask<DraftAccount>() {
        @Override
        protected DraftAccount onLoad(Context context, Bundle args) throws IOException {
            String action = args.getString("action");
            long id = args.getLong("id", -1);
            long reference = args.getLong("reference", -1);
            boolean raw = args.getBoolean("raw", false);
            long answer = args.getLong("answer", -1);

            Log.i(Helper.TAG, "Load draft action=" + action + " id=" + id + " reference=" + reference);

            DraftAccount result = new DraftAccount();

            DB db = DB.getInstance(context);
            try {
                db.beginTransaction();

                result.draft = db.message().getMessage(id);
                if (result.draft == null || result.draft.ui_hide) {
                    if ("edit".equals(action))
                        throw new IllegalStateException("Message to edit not found");
                } else {
                    result.account = db.account().getAccount(result.draft.account);
                    return result;
                }

                EntityMessage ref = db.message().getMessage(reference);
                if (ref == null) {
                    long aid = args.getLong("account", -1);
                    if (aid < 0) {
                        result.account = db.account().getPrimaryAccount();
                        if (result.account == null)
                            throw new IllegalArgumentException(context.getString(R.string.title_no_primary_drafts));
                    } else
                        result.account = db.account().getAccount(aid);
                } else {
                    result.account = db.account().getAccount(ref.account);

                    // Reply to recipient, not to known self
                    List<EntityIdentity> identities = db.identity().getIdentities();

                    if (ref.reply != null && ref.reply.length > 0) {
                        String reply = Helper.canonicalAddress(((InternetAddress) ref.reply[0]).getAddress());
                        for (EntityIdentity identity : identities) {
                            String email = Helper.canonicalAddress(identity.email);
                            if (reply.equals(email)) {
                                ref.reply = null;
                                break;
                            }
                        }
                    }

                    if (ref.deliveredto != null && (ref.to == null || ref.to.length == 0)) {
                        try {
                            Log.i(Helper.TAG, "Setting delivered to=" + ref.deliveredto);
                            ref.to = InternetAddress.parse(ref.deliveredto);
                        } catch (AddressException ex) {
                            Log.w(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                        }
                    }

                    if (ref.from != null && ref.from.length > 0) {
                        String from = Helper.canonicalAddress(((InternetAddress) ref.from[0]).getAddress());
                        Log.i(Helper.TAG, "From=" + from + " to=" + MessageHelper.getFormattedAddresses(ref.to, false));
                        for (EntityIdentity identity : identities) {
                            String email = Helper.canonicalAddress(identity.email);
                            if (from.equals(email)) {
                                Log.i(Helper.TAG, "Swapping from/to");
                                Address[] tmp = ref.to;
                                ref.to = ref.from;
                                ref.from = tmp;
                                break;
                            }
                        }
                    }
                }

                EntityFolder drafts;
                drafts = db.folder().getFolderByType(result.account.id, EntityFolder.DRAFTS);
                if (drafts == null)
                    drafts = db.folder().getPrimaryDrafts();
                if (drafts == null)
                    throw new IllegalArgumentException(getString(R.string.title_no_primary_drafts));

                String body = "";

                result.draft = new EntityMessage();
                result.draft.account = result.account.id;
                result.draft.folder = drafts.id;
                result.draft.msgid = EntityMessage.generateMessageId();

                if (ref == null) {
                    result.draft.thread = result.draft.msgid;

                    try {
                        String to = args.getString("to");
                        result.draft.to = (TextUtils.isEmpty(to) ? null : InternetAddress.parse(to));
                    } catch (AddressException ex) {
                        Log.w(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                    }

                    try {
                        String cc = args.getString("cc");
                        result.draft.cc = (TextUtils.isEmpty(cc) ? null : InternetAddress.parse(cc));
                    } catch (AddressException ex) {
                        Log.w(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                    }

                    try {
                        String bcc = args.getString("bcc");
                        result.draft.bcc = (TextUtils.isEmpty(bcc) ? null : InternetAddress.parse(bcc));
                    } catch (AddressException ex) {
                        Log.w(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                    }

                    result.draft.subject = args.getString("subject");
                    body = args.getString("body");
                    if (body == null)
                        body = "";
                    else
                        body = body.replaceAll("\\r?\\n", "<br />");
                } else {
                    result.draft.thread = ref.thread;

                    if ("reply".equals(action) || "reply_all".equals(action)) {
                        result.draft.replying = ref.id;
                        result.draft.to = (ref.reply == null || ref.reply.length == 0 ? ref.from : ref.reply);
                        result.draft.from = ref.to;

                        if ("reply_all".equals(action)) {
                            List<Address> addresses = new ArrayList<>();
                            if (ref.to != null)
                                addresses.addAll(Arrays.asList(ref.to));
                            if (ref.cc != null)
                                addresses.addAll(Arrays.asList(ref.cc));
                            List<EntityIdentity> identities = db.identity().getIdentities();
                            for (Address address : new ArrayList<>(addresses)) {
                                String cc = Helper.canonicalAddress(((InternetAddress) address).getAddress());
                                for (EntityIdentity identity : identities) {
                                    String email = Helper.canonicalAddress(identity.email);
                                    if (cc.equals(email))
                                        addresses.remove(address);
                                }
                            }
                            result.draft.cc = addresses.toArray(new Address[0]);
                        }

                    } else if ("forward".equals(action)) {
                        result.draft.forwarding = ref.id;
                        result.draft.from = ref.to;
                    }

                    if ("reply".equals(action) || "reply_all".equals(action))
                        result.draft.subject = context.getString(R.string.title_subject_reply, ref.subject);
                    else if ("forward".equals(action))
                        result.draft.subject = context.getString(R.string.title_subject_forward, ref.subject);

                    if (answer > 0 && ("reply".equals(action) || "reply_all".equals(action))) {
                        String text = db.answer().getAnswer(answer).text;

                        String name = null;
                        String email = null;
                        if (result.draft.to != null && result.draft.to.length > 0) {
                            name = ((InternetAddress) result.draft.to[0]).getPersonal();
                            email = ((InternetAddress) result.draft.to[0]).getAddress();
                        }
                        text = text.replace("$name$", name == null ? "" : name);
                        text = text.replace("$email$", email == null ? "" : email);

                        body = text + body;
                    }
                }

                result.draft.content = true;
                result.draft.received = new Date().getTime();
                result.draft.setContactInfo(context);

                result.draft.id = db.message().insertMessage(result.draft);
                result.draft.write(context, body == null ? "" : body);

                if ("new".equals(action)) {
                    ArrayList<Uri> uris = args.getParcelableArrayList("attachments");
                    if (uris != null)
                        for (Uri uri : uris)
                            addAttachment(context, result.draft.id, uri, false);
                } else {
                    int sequence = 0;
                    List<EntityAttachment> attachments = db.attachment().getAttachments(ref.id);
                    for (EntityAttachment attachment : attachments)
                        if (attachment.available &&
                                ("forward".equals(action) || attachment.cid != null)) {
                            EntityAttachment copy = new EntityAttachment();
                            copy.message = result.draft.id;
                            copy.sequence = ++sequence;
                            copy.name = attachment.name;
                            copy.type = attachment.type;
                            copy.cid = attachment.cid;
                            copy.size = attachment.size;
                            copy.progress = attachment.progress;
                            copy.available = attachment.available;
                            copy.id = db.attachment().insertAttachment(copy);

                            File source = EntityAttachment.getFile(context, attachment.id);
                            File target = EntityAttachment.getFile(context, copy.id);
                            Helper.copy(source, target);
                        }

                    if (raw) {
                        EntityAttachment headers = new EntityAttachment();
                        headers.message = result.draft.id;
                        headers.sequence = ++sequence;
                        headers.name = "headers.txt";
                        headers.type = "text/plan";
                        headers.available = true;
                        headers.id = db.attachment().insertAttachment(headers);

                        headers.write(context, ref.headers);

                        EntityAttachment content = new EntityAttachment();
                        content.message = result.draft.id;
                        content.sequence = ++sequence;
                        content.name = "content.html";
                        content.type = "text/html";
                        content.available = true;
                        content.id = db.attachment().insertAttachment(content);

                        File csource = EntityMessage.getFile(context, ref.id);
                        File ctarget = EntityAttachment.getFile(context, content.id);
                        Helper.copy(csource, ctarget);
                    }
                }

                EntityOperation.queue(db, result.draft, EntityOperation.ADD);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            return result;
        }

        @Override
        protected void onLoaded(Bundle args, final DraftAccount result) {
            working = result.draft.id;
            autosave = true;

            final String action = getArguments().getString("action");
            Log.i(Helper.TAG, "Loaded draft id=" + result.draft.id + " action=" + action);

            etExtra.setText(result.draft.extra);
            etTo.setText(MessageHelper.getFormattedAddresses(result.draft.to, true));
            etCc.setText(MessageHelper.getFormattedAddresses(result.draft.cc, true));
            etBcc.setText(MessageHelper.getFormattedAddresses(result.draft.bcc, true));
            etSubject.setText(result.draft.subject);

            etBody.setText(null);

            final Bundle a = new Bundle();
            a.putLong("id", result.draft.id);
            if (result.draft.replying != null)
                a.putLong("reference", result.draft.replying);
            else if (result.draft.forwarding != null)
                a.putLong("reference", result.draft.forwarding);

            new SimpleTask<Spanned[]>() {
                @Override
                protected Spanned[] onLoad(final Context context, Bundle args) throws Throwable {
                    long id = args.getLong("id");
                    final long reference = args.getLong("reference", -1);

                    String body = EntityMessage.read(context, id);
                    String quote = (reference < 0 ? null : HtmlHelper.getQuote(context, reference, true));

                    return new Spanned[]{
                            Html.fromHtml(body, cidGetter, null),
                            quote == null ? null : Html.fromHtml(quote,
                                    new Html.ImageGetter() {
                                        @Override
                                        public Drawable getDrawable(String source) {
                                            return HtmlHelper.decodeImage(source, context, reference, false);
                                        }
                                    },
                                    null)};
                }

                @Override
                protected void onLoaded(Bundle args, Spanned[] texts) {
                    getActivity().invalidateOptionsMenu();
                    etBody.setText(texts[0]);
                    etBody.setSelection(0);

                    tvReference.setText(texts[1]);
                    grpReference.setVisibility(texts[1] == null ? View.GONE : View.VISIBLE);

                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (TextUtils.isEmpty(etTo.getText()))
                                etTo.requestFocus();
                            else if (TextUtils.isEmpty(etSubject.getText()))
                                etSubject.requestFocus();
                            else
                                etBody.requestFocus();
                        }
                    });
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
                }
            }.load(FragmentCompose.this, a);

            getActivity().invalidateOptionsMenu();
            Helper.setViewsEnabled(view, true);

            boolean sender = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("sender", false);

            pbWait.setVisibility(View.GONE);
            grpHeader.setVisibility(View.VISIBLE);
            grpExtra.setVisibility(sender ? View.VISIBLE : View.GONE);
            grpAddresses.setVisibility("reply_all".equals(action) ? View.VISIBLE : View.GONE);
            etBody.setVisibility(View.VISIBLE);
            edit_bar.setVisibility(View.VISIBLE);
            bottom_navigation.setVisibility(View.VISIBLE);

            final DB db = DB.getInstance(getContext());

            db.account().liveAccounts(true).observe(getViewLifecycleOwner(), new Observer<List<EntityAccount>>() {
                private LiveData<List<EntityIdentity>> liveIdentities = null;

                @Override
                public void onChanged(List<EntityAccount> accounts) {
                    if (accounts == null)
                        accounts = new ArrayList<>();

                    Log.i(Helper.TAG, "Set accounts=" + accounts.size());

                    // Sort accounts
                    Collections.sort(accounts, new Comparator<EntityAccount>() {
                        @Override
                        public int compare(EntityAccount a1, EntityAccount a2) {
                            return a1.name.compareTo(a2.name);
                        }
                    });

                    // Show accounts
                    AccountAdapter adapter = new AccountAdapter(getContext(), accounts);
                    adapter.setDropDownViewResource(R.layout.spinner_item1_dropdown);
                    spAccount.setAdapter(adapter);

                    spAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            EntityAccount account = (EntityAccount) parent.getAdapter().getItem(position);

                            if (liveIdentities != null)
                                liveIdentities.removeObservers(getViewLifecycleOwner());
                            liveIdentities = db.identity().liveIdentities(account.id, true);

                            liveIdentities.observe(getViewLifecycleOwner(), new Observer<List<EntityIdentity>>() {
                                @Override
                                public void onChanged(@Nullable List<EntityIdentity> identities) {
                                    if (identities == null)
                                        identities = new ArrayList<>();

                                    Log.i(Helper.TAG, "Set identities=" + identities.size());

                                    // Sort identities
                                    Collections.sort(identities, new Comparator<EntityIdentity>() {
                                        @Override
                                        public int compare(EntityIdentity i1, EntityIdentity i2) {
                                            return i1.toString().compareTo(i2.toString());
                                        }
                                    });

                                    // Show identities
                                    IdentityAdapter adapter = new IdentityAdapter(getContext(), identities);
                                    adapter.setDropDownViewResource(R.layout.spinner_item1_dropdown);
                                    spIdentity.setAdapter(adapter);

                                    boolean found = false;

                                    // Select earlier selected identity
                                    if (result.draft.identity != null)
                                        for (int pos = 0; pos < identities.size(); pos++) {
                                            if (identities.get(pos).id.equals(result.draft.identity)) {
                                                spIdentity.setSelection(pos);
                                                found = true;
                                                break;
                                            }
                                        }

                                    // Select identity matching from address
                                    if (!found && result.draft.from != null && result.draft.from.length > 0) {
                                        String from = Helper.canonicalAddress(((InternetAddress) result.draft.from[0]).getAddress());
                                        for (int pos = 0; pos < identities.size(); pos++) {
                                            String email = Helper.canonicalAddress(identities.get(pos).email);
                                            if (email.equals(from)) {
                                                spIdentity.setSelection(pos);
                                                found = true;
                                                break;
                                            }
                                        }
                                    }

                                    // Select primary identity
                                    if (!found)
                                        for (int pos = 0; pos < identities.size(); pos++)
                                            if (identities.get(pos).primary) {
                                                spIdentity.setSelection(pos);
                                                break;
                                            }
                                }
                            });
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                            IdentityAdapter adapter = new IdentityAdapter(getContext(), new ArrayList<EntityIdentity>());
                            adapter.setDropDownViewResource(R.layout.spinner_item1_dropdown);
                            spIdentity.setAdapter(adapter);
                        }
                    });

                    // Select account
                    for (int pos = 0; pos < accounts.size(); pos++)
                        if (accounts.get(pos).id.equals(result.draft.account)) {
                            spAccount.setSelection(pos);
                            break;
                        }
                }
            });

            db.attachment().liveAttachments(result.draft.id).observe(getViewLifecycleOwner(),
                    new Observer<List<EntityAttachment>>() {
                        @Override
                        public void onChanged(@Nullable List<EntityAttachment> attachments) {
                            if (attachments == null)
                                attachments = new ArrayList<>();

                            adapter.set(attachments);
                            grpAttachments.setVisibility(attachments.size() > 0 ? View.VISIBLE : View.GONE);
                        }
                    });

            db.message().liveMessage(result.draft.id).observe(getViewLifecycleOwner(), new Observer<EntityMessage>() {
                @Override
                public void onChanged(final EntityMessage draft) {
                    // Draft was deleted
                    if (draft == null || draft.ui_hide)
                        finish();
                }
            });
        }

        @Override
        protected void onException(Bundle args, Throwable ex) {
            // External app sending absolute file
            if (ex instanceof FileNotFoundException)
                Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
            else
                Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
        }
    };

    private SimpleTask<EntityMessage> actionLoader = new SimpleTask<EntityMessage>() {
        @Override
        protected EntityMessage onLoad(final Context context, Bundle args) throws Throwable {
            // Get data
            long id = args.getLong("id");
            int action = args.getInt("action");
            long aid = args.getLong("account");
            long iid = args.getLong("identity");
            String extra = args.getString("extra");
            String to = args.getString("to");
            String cc = args.getString("cc");
            String bcc = args.getString("bcc");
            String subject = args.getString("subject");
            String body = args.getString("body");
            boolean empty = args.getBoolean("empty");

            EntityMessage draft;

            DB db = DB.getInstance(context);
            try {
                db.beginTransaction();

                // Get draft & selected identity
                draft = db.message().getMessage(id);
                EntityIdentity identity = db.identity().getIdentity(iid);

                // Draft deleted by server
                if (draft == null)
                    throw new MessageRemovedException("Draft for action was deleted");

                Log.i(Helper.TAG, "Load action id=" + draft.id + " action=" + action);

                // Move draft to new account
                if (draft.account != aid && aid >= 0) {
                    Long uid = draft.uid;
                    String msgid = draft.msgid;

                    draft.uid = null;
                    draft.msgid = null;
                    db.message().updateMessage(draft);

                    draft.id = null;
                    draft.uid = uid;
                    draft.msgid = msgid;
                    draft.content = false;
                    draft.ui_hide = true;
                    draft.id = db.message().insertMessage(draft);
                    EntityOperation.queue(db, draft, EntityOperation.DELETE);

                    draft.id = id;
                    draft.account = aid;
                    draft.folder = db.folder().getFolderByType(aid, EntityFolder.DRAFTS).id;
                    draft.msgid = EntityMessage.generateMessageId();
                    draft.content = true;
                    draft.ui_hide = false;
                    EntityOperation.queue(db, draft, EntityOperation.ADD);
                }

                // Convert data
                InternetAddress afrom[] = (identity == null ? null : new InternetAddress[]{new InternetAddress(identity.email, identity.name)});

                InternetAddress ato[] = null;
                InternetAddress acc[] = null;
                InternetAddress abcc[] = null;

                if (!TextUtils.isEmpty(to))
                    try {
                        ato = InternetAddress.parse(to);
                    } catch (AddressException ignored) {
                    }

                if (!TextUtils.isEmpty(cc))
                    try {
                        acc = InternetAddress.parse(cc);
                    } catch (AddressException ignored) {
                    }

                if (!TextUtils.isEmpty(bcc))
                    try {
                        abcc = InternetAddress.parse(bcc);
                    } catch (AddressException ignored) {
                    }

                if (TextUtils.isEmpty(extra))
                    extra = null;

                // Update draft
                draft.identity = (identity == null ? null : identity.id);
                draft.extra = extra;
                draft.from = afrom;
                draft.to = ato;
                draft.cc = acc;
                draft.bcc = abcc;
                draft.subject = subject;
                draft.received = new Date().getTime();
                db.message().updateMessage(draft);
                draft.write(context, body);

                // Execute action
                if (action == R.id.action_delete) {
                    EntityOperation.queue(db, draft, EntityOperation.DELETE);

                    if (!empty) {
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            public void run() {
                                Toast.makeText(context, R.string.title_draft_deleted, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } else if (action == R.id.action_save || action == R.id.menu_encrypt) {
                    EntityOperation.queue(db, draft, EntityOperation.ADD);

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(context, R.string.title_draft_saved, Toast.LENGTH_LONG).show();
                        }
                    });

                } else if (action == R.id.action_send) {
                    // Check data
                    if (draft.identity == null)
                        throw new IllegalArgumentException(context.getString(R.string.title_from_missing));

                    if (draft.to == null && draft.cc == null && draft.bcc == null)
                        throw new IllegalArgumentException(context.getString(R.string.title_to_missing));

                    // Save attachments
                    List<EntityAttachment> attachments = db.attachment().getAttachments(draft.id);
                    for (EntityAttachment attachment : attachments)
                        if (!attachment.available)
                            throw new IllegalArgumentException(context.getString(R.string.title_attachments_missing));

                    // Delete draft (cannot move to outbox)
                    EntityOperation.queue(db, draft, EntityOperation.DELETE);

                    // Copy message to outbox
                    draft.id = null;
                    draft.folder = db.folder().getOutbox().id;
                    draft.uid = null;
                    draft.msgid = EntityMessage.generateMessageId();
                    draft.ui_hide = false;
                    draft.id = db.message().insertMessage(draft);
                    draft.write(getContext(), body);

                    // Restore attachments
                    for (EntityAttachment attachment : attachments) {
                        File file = EntityAttachment.getFile(context, attachment.id);
                        attachment.id = null;
                        attachment.message = draft.id;
                        attachment.id = db.attachment().insertAttachment(attachment);
                        Helper.copy(file, EntityAttachment.getFile(context, attachment.id));
                    }

                    EntityOperation.queue(db, draft, EntityOperation.SEND);

                    if (draft.replying != null) {
                        EntityMessage replying = db.message().getMessage(draft.replying);
                        EntityOperation.queue(db, replying, EntityOperation.ANSWERED, true);
                    }
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            return draft;
        }

        @Override
        protected void onLoaded(Bundle args, EntityMessage draft) {
            int action = args.getInt("action");
            Log.i(Helper.TAG, "Loaded action id=" + (draft == null ? null : draft.id) + " action=" + action);

            busy = false;
            Helper.setViewsEnabled(view, true);
            getActivity().invalidateOptionsMenu();

            etTo.setText(MessageHelper.getFormattedAddresses(draft.to, true));
            etCc.setText(MessageHelper.getFormattedAddresses(draft.cc, true));
            etBcc.setText(MessageHelper.getFormattedAddresses(draft.bcc, true));

            if (action == R.id.action_delete) {
                autosave = false;
                getFragmentManager().popBackStack();

            } else if (action == R.id.action_save) {
                // Do nothing

            } else if (action == R.id.menu_encrypt) {
                onEncrypt();

            } else if (action == R.id.action_send) {
                autosave = false;
                getFragmentManager().popBackStack();
                Toast.makeText(getContext(), R.string.title_queued, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onException(Bundle args, Throwable ex) {
            busy = false;
            Helper.setViewsEnabled(view, true);
            getActivity().invalidateOptionsMenu();

            if (ex instanceof IllegalArgumentException)
                Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
            else
                Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
        }
    };

    private Html.ImageGetter cidGetter = new Html.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            if (source != null && source.startsWith("cid")) {
                String[] cid = source.split(":");
                if (cid.length == 2 && cid[1].startsWith(BuildConfig.APPLICATION_ID)) {
                    long id = Long.parseLong(cid[1].replace(BuildConfig.APPLICATION_ID + ".", ""));
                    File file = EntityAttachment.getFile(getContext(), id);
                    Drawable d = Drawable.createFromPath(file.getAbsolutePath());
                    if (d != null) {
                        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                        return d;
                    }
                }
            }

            float scale = getContext().getResources().getDisplayMetrics().density;
            int px = Math.round(12 * scale);
            Drawable d = getContext().getResources().getDrawable(R.drawable.baseline_broken_image_24, getContext().getTheme());
            d.setBounds(0, 0, px, px);
            return d;
        }
    };

    private Html.TagHandler tagHandler = new Html.TagHandler() {
        @Override
        public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
            if (tag.equalsIgnoreCase("tt"))
                processTt(opening, output);
        }

        private void processTt(boolean opening, Editable output) {
            Log.i(Helper.TAG, "Handling tt");
            int len = output.length();
            if (opening)
                output.setSpan(new TypefaceSpan("monospace"), len, len, Spannable.SPAN_MARK_MARK);
            else {
                Object span = getLast(output, TypefaceSpan.class);
                if (span != null) {
                    int pos = output.getSpanStart(span);
                    output.removeSpan(span);
                    if (pos != len)
                        output.setSpan(new TypefaceSpan("monospace"), pos, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        private Object getLast(Editable text, Class kind) {
            Object[] spans = text.getSpans(0, text.length(), kind);
            if (spans.length == 0)
                return null;

            for (int i = spans.length; i > 0; i--)
                if (text.getSpanFlags(spans[i - 1]) == Spannable.SPAN_MARK_MARK)
                    return spans[i - 1];

            return null;
        }
    };

    private class DraftAccount {
        EntityMessage draft;
        EntityAccount account;
    }

    public class AccountAdapter extends ArrayAdapter<EntityAccount> {
        private Context context;
        private List<EntityAccount> accounts;

        AccountAdapter(@NonNull Context context, List<EntityAccount> accounts) {
            super(context, 0, accounts);
            this.context = context;
            this.accounts = accounts;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getLayout(position, convertView, parent, R.layout.spinner_item1);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getLayout(position, convertView, parent, R.layout.spinner_item1_dropdown);
        }

        View getLayout(int position, View convertView, ViewGroup parent, int resid) {
            View view = LayoutInflater.from(context).inflate(resid, parent, false);

            EntityAccount account = accounts.get(position);

            TextView text1 = view.findViewById(android.R.id.text1);
            text1.setText(account.name);

            return view;
        }
    }

    public class IdentityAdapter extends ArrayAdapter<EntityIdentity> {
        private Context context;
        private List<EntityIdentity> identities;

        IdentityAdapter(@NonNull Context context, List<EntityIdentity> identities) {
            super(context, 0, identities);
            this.context = context;
            this.identities = identities;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getLayout(position, convertView, parent, R.layout.spinner_item2);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getLayout(position, convertView, parent, R.layout.spinner_item2_dropdown);
        }

        View getLayout(int position, View convertView, ViewGroup parent, int resid) {
            View view = LayoutInflater.from(context).inflate(resid, parent, false);

            EntityIdentity identity = identities.get(position);

            TextView text1 = view.findViewById(android.R.id.text1);
            text1.setText(identity.toString());

            TextView text2 = view.findViewById(android.R.id.text2);
            text2.setText(identity.email);

            return view;
        }
    }

    private ActivityBase.IBackPressedListener onBackPressedListener = new ActivityBase.IBackPressedListener() {
        @Override
        public boolean onBackPressed() {
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                handleExit();
            return true;
        }
    };
}
