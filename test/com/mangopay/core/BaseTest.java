package com.mangopay.core;

import com.mangopay.*;
import com.mangopay.entities.*;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import static org.junit.Assert.*;

@Ignore("Just a base class for tests: nothing to test here")
public abstract class BaseTest {

    protected MangoPayApi _api;

    private static UserNatural _john;
    private static UserLegal _matrix;
    private static BankAccount _johnsAccount;
    private static Wallet _johnsWallet;
    private static Wallet _johnsWalletWithMoney;
    private static PayIn _johnsPayInCardWeb;
    private static PayInPaymentDetailsCard _payInPaymentDetailsCard;
    private static PayInExecutionDetailsWeb _payInExecutionDetailsWeb;
    private static PayOut _johnsPayOutBankWire;
    private static CardRegistration _johnsCardRegistration;

    public BaseTest() {
        this._api = buildNewMangoPayApi();
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    protected final MangoPayApi buildNewMangoPayApi() {
        MangoPayApi api = new MangoPayApi();
        
        // use test client credentails
        api.Config.ClientId = "example";
        api.Config.ClientPassword = "uyWsmnwMQyTnqKgi8Y35A3eVB7bGhqrebYqA1tL6x2vYNpGPiY";
        
        // register storage strategy for tests
        api.OAuthTokenManager.registerCustomStorageStrategy(new DefaultStorageStrategyForTests());
        
        return api;
    }
    
    protected UserNatural getJohn() throws Exception {
        if (BaseTest._john == null) {
            Calendar c = Calendar.getInstance();
            c.set(1975, 12, 21, 0, 0, 0);
            
            UserNatural user = new UserNatural();
            user.FirstName = "John";
            user.LastName = "Doe";
            user.Email = "john.doe@sample.org";
            user.Address = "Some Address";
            user.Birthday = c.getTimeInMillis() / 1000;
            user.Nationality = "FR";
            user.CountryOfResidence = "FR";
            user.Occupation = "programmer";
            user.IncomeRange = 3;
            
            BaseTest._john = (UserNatural)this._api.Users.create(user);
        }
        return BaseTest._john;
    }

    protected UserLegal getMatrix() throws Exception {
        if (BaseTest._matrix == null) {
            UserNatural john = this.getJohn();
            UserLegal user = new UserLegal();
            user.Name = "MartixSampleOrg";
            user.LegalPersonType = "BUSINESS";
            user.HeadquartersAddress = "Some Address";
            user.LegalRepresentativeFirstName = john.FirstName;
            user.LegalRepresentativeLastName = john.LastName;
            user.LegalRepresentativeAddress = john.Address;
            user.LegalRepresentativeEmail = john.Email;
            user.LegalRepresentativeBirthday = john.Birthday;
            user.LegalRepresentativeNationality = john.Nationality;
            user.LegalRepresentativeCountryOfResidence = john.CountryOfResidence;
            
            BaseTest._matrix = (UserLegal)this._api.Users.create(user);
        }
        return BaseTest._matrix;
    }

    protected BankAccount getJohnsAccount() throws Exception {
        if (BaseTest._johnsAccount == null) {
            UserNatural john = this.getJohn();
            BankAccount account = new BankAccount();
            account.Type = "IBAN";
            account.OwnerName = john.FirstName + " " + john.LastName;
            account.OwnerAddress = john.Address;
            account.IBAN = "AD12 0001 2030 2003 5910 0100";
            account.BIC = "BINAADADXXX";
            BaseTest._johnsAccount = this._api.Users.createBankAccount(john.Id, account);
        }
        return BaseTest._johnsAccount;
    }
    
    protected Wallet getJohnsWallet() throws Exception {
        if (BaseTest._johnsWallet == null) {
            UserNatural john = this.getJohn();
            
            Wallet wallet = new Wallet();
            wallet.Owners = new ArrayList<>();
            wallet.Owners.add(john.Id);
            
            wallet.Currency = "EUR";
            wallet.Description = "WALLET IN EUR";
            
            BaseTest._johnsWallet = this._api.Wallets.create(wallet);
            
            if (BaseTest._johnsWallet.Balance.Amount == null) {
                BaseTest._johnsWallet.Balance.Amount = 0.0;
            }
        }
        
        return BaseTest._johnsWallet;
    }
    
    /**
     * Creates wallet for John, loaded with 10k EUR (John's got lucky) if not 
     * created yet, or returns an existing one.
     * @return Wallet instance loaded with 10k EUR.
     */
    protected Wallet getJohnsWalletWithMoney() throws Exception {
        return getJohnsWalletWithMoney(10000);
    }
    
    /**
     * Creates wallet for John, if not created yet, or returns an existing one.
     * @param amount Initial wallet's money amount.
     * @return Wallet entity instance.
     */
    protected Wallet getJohnsWalletWithMoney(double amount) throws Exception {
        
        if (BaseTest._johnsWalletWithMoney == null) {
            
            UserNatural john = this.getJohn();
            
            // create wallet with money
            Wallet wallet = new Wallet();
            wallet.Owners = new ArrayList<>();
            wallet.Owners.add(john.Id);
            wallet.Currency = "EUR";
            wallet.Description = "WALLET IN EUR WITH MONEY";
            
            BaseTest._johnsWalletWithMoney = this._api.Wallets.create(wallet);
            
            CardRegistration cardRegistration = new CardRegistration();
            cardRegistration.UserId = BaseTest._johnsWalletWithMoney.Owners.get(0);
            cardRegistration.Currency = "EUR";
            cardRegistration = this._api.CardRegistrations.create(cardRegistration);
            
            cardRegistration.RegistrationData = this.getPaylineCorrectRegistartionData(cardRegistration);
            cardRegistration = this._api.CardRegistrations.update(cardRegistration);
            
            Card card = this._api.Cards.get(cardRegistration.CardId);
            
            // create pay-in CARD DIRECT
            PayIn payIn = new PayIn();
            payIn.CreditedWalletId = BaseTest._johnsWalletWithMoney.Id;
            payIn.AuthorId = cardRegistration.UserId;
            payIn.DebitedFunds = new Money();
            payIn.DebitedFunds.Amount = amount;
            payIn.DebitedFunds.Currency = "EUR";
            payIn.Fees = new Money();
            payIn.Fees.Amount = 0.0;
            payIn.Fees.Currency = "EUR";

            // payment type as CARD
            payIn.PaymentDetails = new PayInPaymentDetailsCard();
            if (card.CardType.equals("CB") || card.CardType.equals("VISA") || card.CardType.equals("MASTERCARD"))
                ((PayInPaymentDetailsCard)payIn.PaymentDetails).CardType = "CB_VISA_MASTERCARD";
            else if (card.CardType.equals("AMEX"))
                ((PayInPaymentDetailsCard)payIn.PaymentDetails).CardType = "AMEX";

            // execution type as DIRECT
            payIn.ExecutionDetails = new PayInExecutionDetailsDirect();
            ((PayInExecutionDetailsDirect)payIn.ExecutionDetails).CardId = card.Id;
            ((PayInExecutionDetailsDirect)payIn.ExecutionDetails).SecureModeReturnURL = "http://test.com";
            // create Pay-In
            this._api.PayIns.create(payIn);
        }
        
        return this._api.Wallets.get(BaseTest._johnsWalletWithMoney.Id);
    }
    
    private PayInPaymentDetailsCard getPayInPaymentDetailsCard() {
        if (BaseTest._payInPaymentDetailsCard == null) {
            BaseTest._payInPaymentDetailsCard = new PayInPaymentDetailsCard();
            BaseTest._payInPaymentDetailsCard.CardType = "AMEX";
        }
        
        return BaseTest._payInPaymentDetailsCard;
    }
    
    private PayInExecutionDetailsWeb getPayInExecutionDetailsWeb() {
        if (BaseTest._payInExecutionDetailsWeb == null) {
            BaseTest._payInExecutionDetailsWeb = new PayInExecutionDetailsWeb();
            BaseTest._payInExecutionDetailsWeb.TemplateURL = "https://TemplateURL.com";
            BaseTest._payInExecutionDetailsWeb.SecureMode = "DEFAULT";
            BaseTest._payInExecutionDetailsWeb.Culture = "fr";
            BaseTest._payInExecutionDetailsWeb.ReturnURL = "https://test.com";
        }
        
        return BaseTest._payInExecutionDetailsWeb;
    }
    
    protected PayIn getJohnsPayInCardWeb() throws Exception {
        if (BaseTest._johnsPayInCardWeb == null) {
            Wallet wallet = this.getJohnsWallet();
            UserNatural user = this.getJohn();
            
            PayIn payIn = new PayIn();
            payIn.AuthorId = user.Id;
            payIn.CreditedUserId = user.Id;
            payIn.DebitedFunds = new Money();
            payIn.DebitedFunds.Currency = "EUR";
            payIn.DebitedFunds.Amount = 1000.0;
            payIn.Fees = new Money();
            payIn.Fees.Currency = "EUR";
            payIn.Fees.Amount = 5.0;
            payIn.CreditedWalletId = wallet.Id;
            payIn.PaymentDetails = this.getPayInPaymentDetailsCard();
            payIn.ExecutionDetails = this.getPayInExecutionDetailsWeb();
            
            BaseTest._johnsPayInCardWeb = this._api.PayIns.create(payIn);
        }
        
        return BaseTest._johnsPayInCardWeb;
    }
    
    /**
     * Creates Pay-In Card Direct object
     * @return PayIn
     */
    protected PayIn getNewPayInCardDirect() throws Exception {
        
        Wallet wallet = this.getJohnsWalletWithMoney();
        UserNatural user = this.getJohn();

        CardRegistration cardRegistration = new CardRegistration();
        cardRegistration.UserId = user.Id;
        cardRegistration.Currency = "EUR";
        cardRegistration = this._api.CardRegistrations.create(cardRegistration);
        cardRegistration.RegistrationData = this.getPaylineCorrectRegistartionData(cardRegistration);
        cardRegistration = this._api.CardRegistrations.update(cardRegistration);

        Card card = this._api.Cards.get(cardRegistration.CardId);

        // create pay-in CARD DIRECT
        PayIn payIn = new PayIn();
        payIn.CreditedWalletId = wallet.Id;
        payIn.AuthorId = user.Id;
        payIn.DebitedFunds = new Money();
        payIn.DebitedFunds.Amount = 10000.0;
        payIn.DebitedFunds.Currency = "EUR";
        payIn.Fees = new Money();
        payIn.Fees.Amount = 0.0;
        payIn.Fees.Currency = "EUR";

        // payment type as CARD
        payIn.PaymentDetails = new PayInPaymentDetailsCard();
        if (card.CardType.equals("CB") || card.CardType.equals("VISA") || card.CardType.equals("MASTERCARD"))
            ((PayInPaymentDetailsCard)payIn.PaymentDetails).CardType = "CB_VISA_MASTERCARD";
        else if (card.CardType.equals("AMEX"))
            ((PayInPaymentDetailsCard)payIn.PaymentDetails).CardType = "AMEX";

        // execution type as DIRECT
        payIn.ExecutionDetails = new PayInExecutionDetailsDirect();
        ((PayInExecutionDetailsDirect)payIn.ExecutionDetails).CardId = card.Id;
        ((PayInExecutionDetailsDirect)payIn.ExecutionDetails).SecureModeReturnURL = "http://test.com";
            
        return this._api.PayIns.create(payIn);
    }
    
    protected PayOut getJohnsPayOutBankWire() throws Exception {
        if (BaseTest._johnsPayOutBankWire == null) {
            Wallet wallet = this.getJohnsWallet();
            UserNatural user = this.getJohn();
            BankAccount account = this.getJohnsAccount();
            
            PayOut payOut = new PayOut();
            payOut.Tag = "DefaultTag";
            payOut.AuthorId = user.Id;
            payOut.CreditedUserId = user.Id;
            payOut.DebitedFunds = new Money();
            payOut.DebitedFunds.Currency = "EUR";
            payOut.DebitedFunds.Amount = 10.0;
            payOut.Fees = new Money();
            payOut.Fees.Currency = "EUR";
            payOut.Fees.Amount = 5.0;
            
            payOut.DebitedWalletId = wallet.Id;
            payOut.MeanOfPaymentDetails = new PayOutPaymentDetailsBankWire();
            ((PayOutPaymentDetailsBankWire)payOut.MeanOfPaymentDetails).BankAccountId = account.Id;
            ((PayOutPaymentDetailsBankWire)payOut.MeanOfPaymentDetails).Communication = "Communication text";
            
            BaseTest._johnsPayOutBankWire = this._api.PayOuts.create(payOut);
        }
        
        return BaseTest._johnsPayOutBankWire;
    }
    
    protected Transfer getNewTransfer() throws Exception {
        Wallet walletWithMoney = this.getJohnsWalletWithMoney();
        UserNatural user = this.getJohn();

        Wallet wallet = new Wallet();
        wallet.Owners = new ArrayList<>();
        wallet.Owners.add(user.Id);
        wallet.Currency = "EUR";
        wallet.Description = "WALLET IN EUR FOR TRANSFER";
        wallet = this._api.Wallets.create(wallet);

        Transfer transfer = new Transfer();
        transfer.Tag = "DefaultTag";
        transfer.AuthorId = user.Id;
        transfer.CreditedUserId = user.Id;
        transfer.DebitedFunds = new Money();
        transfer.DebitedFunds.Currency = "EUR";
        transfer.DebitedFunds.Amount = 100.0;
        transfer.Fees = new Money();
        transfer.Fees.Currency = "EUR";
        transfer.Fees.Amount = 0.0;

        transfer.DebitedWalletId = walletWithMoney.Id;
        transfer.CreditedWalletId = wallet.Id;
        
        return this._api.Transfers.create(transfer);
    }
    
    /**
     * Creates refund object for transfer.
     */
    protected Refund getNewRefundForTransfer(Transfer transfer) throws Exception {
        UserNatural user = this.getJohn();

        Refund refund = new Refund();
        refund.DebitedWalletId = transfer.DebitedWalletId;
        refund.CreditedWalletId = transfer.CreditedWalletId;
        refund.AuthorId = user.Id;
        refund.DebitedFunds = new Money();
        refund.DebitedFunds.Amount = transfer.DebitedFunds.Amount;
        refund.DebitedFunds.Currency = transfer.DebitedFunds.Currency;
        refund.Fees = new Money();
        refund.Fees.Amount = transfer.Fees.Amount;
        refund.Fees.Currency = transfer.Fees.Currency;
        
        return this._api.Transfers.createRefund(transfer.Id, refund);
    }

    /**
     * Creates refund object for PayIn.
     * @return Created Refund entity.
     */
    protected Refund getNewRefundForPayIn(PayIn payIn) throws Exception {
        UserNatural user = this.getJohn();

        Refund refund = new Refund();
        refund.CreditedWalletId = payIn.CreditedWalletId;
        refund.AuthorId = user.Id;
        refund.DebitedFunds = new Money();
        refund.DebitedFunds.Amount = payIn.DebitedFunds.Amount;
        refund.DebitedFunds.Currency = payIn.DebitedFunds.Currency;
        refund.Fees = new Money();
        refund.Fees.Amount = payIn.Fees.Amount;
        refund.Fees.Currency = payIn.Fees.Currency;
        
        return this._api.PayIns.createRefund(payIn.Id, refund);
    }
    
    /**
     * Creates card registration object.
     * @return CardRegistration instance returned from API.
     */
    protected CardRegistration getJohnsCardRegistration() throws Exception {
        if (BaseTest._johnsCardRegistration == null) {
            UserNatural user = this.getJohn();
            
            CardRegistration cardRegistration = new CardRegistration();
            cardRegistration.UserId = user.Id;
            cardRegistration.Currency = "EUR";

            BaseTest._johnsCardRegistration = this._api.CardRegistrations.create(cardRegistration);
        }
        
        return BaseTest._johnsCardRegistration;
    }
    
    /**
     * Gets registration data from Payline service.
     * @param cardRegistration
     * @return Registration data.
     */
    protected String getPaylineCorrectRegistartionData(CardRegistration cardRegistration) throws MalformedURLException, IOException, Exception {
        
        String data = "data=" + cardRegistration.PreregistrationData +
                "&accessKeyRef=" + cardRegistration.AccessKey +
                "&cardNumber=4970101122334406" +
                "&cardExpirationDate=1214" +
                "&cardCvx=123";

        URL url = new URL(cardRegistration.CardRegistrationURL);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        
        connection.setRequestMethod("POST");
        connection.setUseCaches (false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        
        try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
            wr.writeBytes(data);
            wr.flush ();
        }
        
        int responseCode = connection.getResponseCode();
        InputStream is;
        if (responseCode != 200) {
            is = connection.getErrorStream();
        } else {
            is = connection.getInputStream();
        }

        StringBuffer resp;
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(is))) {
            String line;
            resp = new StringBuffer();
            while((line = rd.readLine()) != null) {
                resp.append(line);
            }
        }
        String responseString = resp.toString();
        
        if (responseCode == 200)
            return responseString;
        else
            throw new Exception(responseString);
    }
    
    protected <T> void assertEqualInputProps(T entity1, T entity2) throws Exception {

        if (entity1 instanceof UserNatural) {
            assertEquals(((UserNatural)entity1).Tag, ((UserNatural)entity2).Tag);
            assertEquals(((UserNatural)entity1).PersonType, ((UserNatural)entity2).PersonType);
            assertEquals(((UserNatural)entity1).FirstName, ((UserNatural)entity2).FirstName);
            assertEquals(((UserNatural)entity1).LastName, ((UserNatural)entity2).LastName);
            assertEquals(((UserNatural)entity1).Email, ((UserNatural)entity2).Email);
            assertEquals(((UserNatural)entity1).Address, ((UserNatural)entity2).Address);
            assertEquals(((UserNatural)entity1).Birthday, ((UserNatural)entity2).Birthday);
            assertEquals(((UserNatural)entity1).Nationality, ((UserNatural)entity2).Nationality);
            assertEquals(((UserNatural)entity1).CountryOfResidence, ((UserNatural)entity2).CountryOfResidence);
            assertEquals(((UserNatural)entity1).Occupation, ((UserNatural)entity2).Occupation);
            assertEquals(((UserNatural)entity1).IncomeRange, ((UserNatural)entity2).IncomeRange);

        } else if (entity1 instanceof UserLegal) {
            assertEquals(((UserLegal)entity1).Tag, ((UserLegal)entity2).Tag);
            assertEquals(((UserLegal)entity1).PersonType, ((UserLegal)entity2).PersonType);
            assertEquals(((UserLegal)entity1).Name, ((UserLegal)entity2).Name);
            assertEquals(((UserLegal)entity1).HeadquartersAddress, ((UserLegal)entity2).HeadquartersAddress);
            assertEquals(((UserLegal)entity1).LegalRepresentativeFirstName, ((UserLegal)entity2).LegalRepresentativeFirstName);
            assertEquals(((UserLegal)entity1).LegalRepresentativeLastName, ((UserLegal)entity2).LegalRepresentativeLastName);
            assertEquals("***** TEMPORARY API ISSUE: RETURNED OBJECT MISSES THIS PROP AFTER CREATION *****", ((UserLegal)entity1).LegalRepresentativeAddress, ((UserLegal)entity2).LegalRepresentativeAddress);
            assertEquals(((UserLegal)entity1).LegalRepresentativeEmail, ((UserLegal)entity2).LegalRepresentativeEmail);
            assertEquals("***** TEMPORARY API ISSUE: RETURNED OBJECT HAS THIS PROP CHANGED FROM TIMESTAMP INTO ISO STRING AFTER CREATION *****", ((UserLegal)entity1).LegalRepresentativeBirthday, ((UserLegal)entity2).LegalRepresentativeBirthday);
            assertEquals(((UserLegal)entity1).LegalRepresentativeNationality, ((UserLegal)entity2).LegalRepresentativeNationality);
            assertEquals(((UserLegal)entity1).LegalRepresentativeCountryOfResidence, ((UserLegal)entity2).LegalRepresentativeCountryOfResidence);

        } else if (entity1 instanceof BankAccount) {
            assertEquals(((BankAccount)entity1).Tag, ((BankAccount)entity2).Tag);
            assertEquals(((BankAccount)entity1).UserId, ((BankAccount)entity2).UserId);
            assertEquals(((BankAccount)entity1).Type, ((BankAccount)entity2).Type);
            assertEquals(((BankAccount)entity1).OwnerName, ((BankAccount)entity2).OwnerName);
            assertEquals(((BankAccount)entity1).OwnerAddress, ((BankAccount)entity2).OwnerAddress);
            assertEquals(((BankAccount)entity1).IBAN, ((BankAccount)entity2).IBAN);
            assertEquals(((BankAccount)entity1).BIC, ((BankAccount)entity2).BIC);
            
        } else if (entity1 instanceof PayIn) {
            assertEquals(((PayIn)entity1).Tag, ((PayIn)entity2).Tag);
            assertEquals(((PayIn)entity1).AuthorId, ((PayIn)entity2).AuthorId);
            assertEquals(((PayIn)entity1).CreditedUserId, ((PayIn)entity2).CreditedUserId);
            
            assertEqualInputProps(((PayIn)entity1).DebitedFunds, ((PayIn)entity2).DebitedFunds);
            assertEqualInputProps(((PayIn)entity1).CreditedFunds, ((PayIn)entity2).CreditedFunds);
            assertEqualInputProps(((PayIn)entity1).Fees, ((PayIn)entity2).Fees);
            
        } else if (entity1 instanceof PayInPaymentDetailsCard) {
            assertEquals(((PayInPaymentDetailsCard)entity1).CardType, ((PayInPaymentDetailsCard)entity2).CardType);
            
        } else if (entity1 instanceof PayInExecutionDetailsWeb) {
            assertEquals(((PayInExecutionDetailsWeb)entity1).TemplateURL, ((PayInExecutionDetailsWeb)entity2).TemplateURL);
            assertEquals(((PayInExecutionDetailsWeb)entity1).Culture, ((PayInExecutionDetailsWeb)entity2).Culture);
            assertEquals(((PayInExecutionDetailsWeb)entity1).SecureMode, ((PayInExecutionDetailsWeb)entity2).SecureMode);
            assertEquals(((PayInExecutionDetailsWeb)entity1).RedirectURL, ((PayInExecutionDetailsWeb)entity2).RedirectURL);
            assertEquals(((PayInExecutionDetailsWeb)entity1).ReturnURL, ((PayInExecutionDetailsWeb)entity2).ReturnURL);
            
        } else if (entity1 instanceof PayOut) {
            assertEquals(((PayOut)entity1).Tag, ((PayOut)entity2).Tag);
            assertEquals(((PayOut)entity1).AuthorId, ((PayOut)entity2).AuthorId);
            assertEquals(((PayOut)entity1).CreditedUserId, ((PayOut)entity2).CreditedUserId);
            
            assertEqualInputProps(((PayOut)entity1).DebitedFunds, ((PayOut)entity2).DebitedFunds);
            assertEqualInputProps(((PayOut)entity1).CreditedFunds, ((PayOut)entity2).CreditedFunds);
            assertEqualInputProps(((PayOut)entity1).Fees, ((PayOut)entity2).Fees);
            assertEqualInputProps(((PayOut)entity1).MeanOfPaymentDetails, ((PayOut)entity2).MeanOfPaymentDetails);
            
        } else if (entity1 instanceof Transfer) {
            assertEquals(((Transfer)entity1).Tag, ((Transfer)entity2).Tag);
            assertEquals(((Transfer)entity1).AuthorId, ((Transfer)entity2).AuthorId);
            assertEquals(((Transfer)entity1).CreditedUserId, ((Transfer)entity2).CreditedUserId);
            
            assertEqualInputProps(((Transfer)entity1).DebitedFunds, ((Transfer)entity2).DebitedFunds);
            assertEqualInputProps(((Transfer)entity1).CreditedFunds, ((Transfer)entity2).CreditedFunds);
            assertEqualInputProps(((Transfer)entity1).Fees, ((Transfer)entity2).Fees);
            
        } else if (entity1 instanceof PayOutPaymentDetailsBankWire) {
            assertEquals(((PayOutPaymentDetailsBankWire)entity1).BankAccountId, ((PayOutPaymentDetailsBankWire)entity2).BankAccountId);
            assertEquals(((PayOutPaymentDetailsBankWire)entity1).Communication, ((PayOutPaymentDetailsBankWire)entity2).Communication);
            
        } else if (entity1 instanceof Transaction) {
            assertEquals(((Transaction)entity1).Tag, ((Transaction)entity2).Tag);
            
            assertEqualInputProps(((Transaction)entity1).DebitedFunds, ((Transaction)entity2).DebitedFunds);
            assertEqualInputProps(((Transaction)entity1).Fees, ((Transaction)entity2).Fees);
            assertEqualInputProps(((Transaction)entity1).CreditedFunds, ((Transaction)entity2).CreditedFunds);
            
            assertEquals(((Transaction)entity1).Status, ((Transaction)entity2).Status);

        } else if (entity1 instanceof Money) {
            assertEquals(((Money)entity1).Currency, ((Money)entity2).Currency);
            assertEquals(((Money)entity1).Amount, ((Money)entity2).Amount);
        } else {
            throw new Exception("Unsupported type");
        }
        
    }
    
}
