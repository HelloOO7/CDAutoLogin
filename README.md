# CDAutoLogin

Jednoduchá aplikace, která umožňuje automatické přihlášení k Wi-Fi vlaků Českých drah.

## Princip fungování

Pro přihlášení k ČD Wi-Fi je obvykle potřeba projít tzv. "captive portálem", tedy bránou v prohlížeči,
ve které se odsouhlasí podmínky použití a podobně. Tato aplikace uvedené kroky, ovšemže za předpokladu,
že s podmínkami ČD souhlasíte (ne že by si je někdo četl), provede automaticky za vás na pozadí.
To se hodí například, pokud si chcete při nástupu zobrazit plánek sedadel nebo jiné informace o vlaku,
ale nechcete kvůli tomu proskakovat obručemi captive portálu.

## Přihlášení s mobilními daty

Pokud máte aktivní datové připojení přes telefonní síť, operační systém nepošle aplikaci signál, že
jste připojeni k Wi-Fi, dokud k ní nejste přihlášeni (tedy včetně captive portálu). Tím bohužel vzniká
takový problém vejce nebo slepice, nicméně i v tomto případě bude aplikace fungovat, jen je potřeba
ji o přihlášení požádat tlačítkem (jediným) na hlavní obrazovce. Proces si v tomto případě sám pro
sebe vynutí komunikaci přes Wi-Fi a telefonní připojení bude ignorovat.

## Frekvence kontroly

Aplikace je nastavená tak, aby se spustila vždy při prvním připojení k libovolné síti s captive portálem
(nastavit filtr pouze na sítě ČD pro perzistentní operace v Androidu
[nejde](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setRequiredNetwork(android.net.NetworkRequest))). To se často
může stát už na nádraží, kde je slabý signál, a tudíž přihlášení může selhat. V takovém případě proběhne
pokus o opětovné přihlášení o minutu později, pokud byl příčinou vypršelý timeout spojení (tedy pravděpodobně
slabý signál), v opačném případě (třeba pokud jste na jiné než českodrážní Wi-Fi) za 5 minut. Po úspěšném
přihlášení se čekání na captive síť aktivuje znovu rovněž za jednu minutu.