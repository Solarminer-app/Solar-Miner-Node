import Image from 'next/image';

type AppLogoProps = {
    variant?: 'mark' | 'full';
    priority?: boolean;
    className?: string;
};

export default function AppLogo({variant = 'mark', priority = false, className = ''}: AppLogoProps) {
    if (variant === 'full') {
        return (
            <Image
                alt="Solarminer.app"
                className={`h-auto w-52 sm:w-60 ${className}`}
                height={560}
                priority={priority}
                src="/logo.png"
                width={560}
            />
        );
    }

    return (
        <span aria-hidden="true" className={`relative block h-10 w-10 shrink-0 overflow-hidden ${className}`}>
            <Image
                alt=""
                className="absolute left-[-14px] top-[-10px] h-[68px] w-[68px] max-w-none"
                height={560}
                priority={priority}
                src="/logo.png"
                width={560}
            />
        </span>
    );
}
